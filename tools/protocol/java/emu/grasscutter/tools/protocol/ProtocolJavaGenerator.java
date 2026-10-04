package emu.grasscutter.tools.protocol;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Regenerates Java protocol sources from the canonical 7.1 descriptor set and recovered additions. */
public final class ProtocolJavaGenerator {
    private static final int FILES_PER_PROTOC_INVOCATION = 128;
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:(?:public|weak)\\s+)?\"([^\"]+)\"\\s*;.*$");

    private ProtocolJavaGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: ProtocolJavaGenerator <protoc> <descriptor-set> <file-list> <supplemental-proto-dir> <java-out>");
        }

        var protoc = Path.of(args[0]).toAbsolutePath().normalize();
        var descriptorSet = Path.of(args[1]).toAbsolutePath().normalize();
        var fileList = Path.of(args[2]).toAbsolutePath().normalize();
        var supplementalProtoDir = Path.of(args[3]).toAbsolutePath().normalize();
        var javaOut = Path.of(args[4]).toAbsolutePath().normalize();

        requireRegularFile(protoc, "protoc executable");
        requireRegularFile(descriptorSet, "descriptor set");
        requireRegularFile(fileList, "protocol file list");
        requireDirectory(supplementalProtoDir, "supplemental proto directory");

        var protocolFiles = Files.readAllLines(fileList, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .sorted()
                .toList();
        if (protocolFiles.isEmpty()) {
            throw new IllegalStateException("protocol file list is empty: " + fileList);
        }

        List<String> supplementalProtoFiles;
        try (Stream<Path> files = Files.walk(supplementalProtoDir)) {
            supplementalProtoFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".proto"))
                    .map(supplementalProtoDir::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
        var descriptorImports =
                findDescriptorImports(supplementalProtoDir, supplementalProtoFiles);

        recreateDirectory(javaOut);

        for (int offset = 0; offset < protocolFiles.size(); offset += FILES_PER_PROTOC_INVOCATION) {
            var end = Math.min(offset + FILES_PER_PROTOC_INVOCATION, protocolFiles.size());
            runDescriptorProtoc(protoc, descriptorSet, javaOut, protocolFiles.subList(offset, end));
        }

        if (!supplementalProtoFiles.isEmpty()) {
            runSourceProtoc(
                    protoc,
                    descriptorSet,
                    supplementalProtoDir,
                    javaOut,
                    descriptorImports,
                    supplementalProtoFiles);
        }

        long javaFileCount;
        try (Stream<Path> generatedFiles = Files.walk(javaOut)) {
            javaFileCount = generatedFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
        }
        var expectedJavaFileCount = protocolFiles.size() + supplementalProtoFiles.size();
        if (javaFileCount != expectedJavaFileCount) {
            throw new IllegalStateException(
                    "protoc generated "
                            + javaFileCount
                            + " Java files; expected "
                            + expectedJavaFileCount
                            + " from "
                            + protocolFiles.size()
                            + " descriptor files and "
                            + supplementalProtoFiles.size()
                            + " supplemental proto files");
        }

        System.out.printf(
                "Generated %,d Java files from %,d protocol descriptors and %,d supplemental proto files.%n",
                javaFileCount, protocolFiles.size(), supplementalProtoFiles.size());
    }

    private static List<String> findDescriptorImports(
            Path supplementalProtoDir, List<String> supplementalProtoFiles) throws IOException {
        var supplementalSet = Set.copyOf(supplementalProtoFiles);
        var descriptorImports = new TreeSet<String>();
        for (var protoFile : supplementalProtoFiles) {
            for (var line : Files.readAllLines(supplementalProtoDir.resolve(protoFile), StandardCharsets.UTF_8)) {
                var matcher = IMPORT_PATTERN.matcher(line);
                if (matcher.matches() && !supplementalSet.contains(matcher.group(1))) {
                    descriptorImports.add(matcher.group(1));
                }
            }
        }
        return List.copyOf(descriptorImports);
    }

    private static void runDescriptorProtoc(
            Path protoc, Path descriptorSet, Path javaOut, List<String> protocolFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(protocolFiles.size() + 3);
        command.add(protoc.toString());
        command.add("--descriptor_set_in=" + descriptorSet);
        command.add("--java_out=" + javaOut);
        command.addAll(protocolFiles);

        runProtoc(command, protocolFiles.getFirst() + " .. " + protocolFiles.getLast());
    }

    private static void runSourceProtoc(
            Path protoc,
            Path descriptorSet,
            Path supplementalProtoDir,
            Path javaOut,
            List<String> descriptorImports,
            List<String> supplementalProtoFiles)
            throws IOException, InterruptedException {
        var command =
                new ArrayList<String>(descriptorImports.size() + supplementalProtoFiles.size() + 5);
        command.add(protoc.toString());
        command.add("--descriptor_set_in=" + descriptorSet);
        command.add("--proto_path=" + supplementalProtoDir);
        command.add("--java_out=" + javaOut);
        command.addAll(descriptorImports);
        command.addAll(supplementalProtoFiles);

        runProtoc(command, "supplemental 7.1 protocol sources");
    }

    private static void runProtoc(List<String> command, String description)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "protoc failed with exit code "
                            + exitCode
                            + " while generating "
                            + description
                            + (output.isBlank() ? "" : ":\n" + output));
        }
        if (!output.isBlank()) {
            System.out.print(output);
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }

    private static void requireRegularFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }

    private static void requireDirectory(Path path, String description) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }
}
