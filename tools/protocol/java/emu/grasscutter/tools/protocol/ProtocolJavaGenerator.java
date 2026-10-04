package emu.grasscutter.tools.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Regenerates Java protocol sources from the canonical 7.1 descriptor set. */
public final class ProtocolJavaGenerator {
    private static final int FILES_PER_PROTOC_INVOCATION = 128;

    private ProtocolJavaGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: ProtocolJavaGenerator <protoc> <descriptor-set> <file-list> <java-out>");
        }

        var protoc = Path.of(args[0]).toAbsolutePath().normalize();
        var descriptorSet = Path.of(args[1]).toAbsolutePath().normalize();
        var fileList = Path.of(args[2]).toAbsolutePath().normalize();
        var javaOut = Path.of(args[3]).toAbsolutePath().normalize();

        requireRegularFile(protoc, "protoc executable");
        requireRegularFile(descriptorSet, "descriptor set");
        requireRegularFile(fileList, "protocol file list");

        var protocolFiles = Files.readAllLines(fileList, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .sorted()
                .toList();
        if (protocolFiles.isEmpty()) {
            throw new IllegalStateException("protocol file list is empty: " + fileList);
        }

        recreateDirectory(javaOut);

        for (int offset = 0; offset < protocolFiles.size(); offset += FILES_PER_PROTOC_INVOCATION) {
            var end = Math.min(offset + FILES_PER_PROTOC_INVOCATION, protocolFiles.size());
            runProtoc(protoc, descriptorSet, javaOut, protocolFiles.subList(offset, end));
        }

        long javaFileCount;
        try (Stream<Path> generatedFiles = Files.walk(javaOut)) {
            javaFileCount = generatedFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
        }
        if (javaFileCount == 0) {
            throw new IllegalStateException("protoc generated no Java sources in " + javaOut);
        }

        System.out.printf(
                "Generated %,d Java files from %,d protocol descriptors.%n",
                javaFileCount, protocolFiles.size());
    }

    private static void runProtoc(
            Path protoc, Path descriptorSet, Path javaOut, List<String> protocolFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(protocolFiles.size() + 3);
        command.add(protoc.toString());
        command.add("--descriptor_set_in=" + descriptorSet);
        command.add("--java_out=" + javaOut);
        command.addAll(protocolFiles);

        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "protoc failed with exit code "
                            + exitCode
                            + " while generating "
                            + protocolFiles.getFirst()
                            + " .. "
                            + protocolFiles.getLast()
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
}
