package emu.grasscutter.tools.protocol;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Exports the descriptors embedded in the checked-in 7.1 generated protocol classes. */
public final class ProtocolDescriptorExporter {
    private static final String PROTOCOL_PACKAGE_PATH = "emu/grasscutter/net/proto";

    private ProtocolDescriptorExporter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: ProtocolDescriptorExporter <classes-root> <descriptor-set> <file-list>");
        }

        var classesRoot = Path.of(args[0]).toAbsolutePath().normalize();
        var descriptorSetPath = Path.of(args[1]).toAbsolutePath().normalize();
        var fileListPath = Path.of(args[2]).toAbsolutePath().normalize();
        var protocolClasses = classesRoot.resolve(PROTOCOL_PACKAGE_PATH);

        if (!Files.isDirectory(protocolClasses)) {
            throw new IllegalStateException("protocol classes were not compiled: " + protocolClasses);
        }

        var descriptors = new TreeMap<String, FileDescriptorProto>();
        var rootFiles = new TreeSet<String>();
        var failures = new ArrayList<String>();
        var classLoader = ProtocolDescriptorExporter.class.getClassLoader();

        for (var classFile : topLevelClassFiles(protocolClasses)) {
            var className = toClassName(classesRoot, classFile);
            try {
                var type = Class.forName(className, false, classLoader);
                var getDescriptor = fileDescriptorMethod(type);
                if (getDescriptor == null) {
                    continue;
                }

                var descriptor = (FileDescriptor) getDescriptor.invoke(null);
                rootFiles.add(descriptor.getName());
                collectDescriptor(descriptor, descriptors);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InvocationTargetException
                    | LinkageError error) {
                failures.add(className + ": " + rootCauseMessage(error));
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "failed to read "
                            + failures.size()
                            + " protocol descriptor carriers:\n"
                            + String.join("\n", failures));
        }
        if (rootFiles.isEmpty()) {
            throw new IllegalStateException("no protocol file descriptors were found");
        }

        var descriptorSet = FileDescriptorSet.newBuilder();
        descriptors.values().forEach(descriptorSet::addFile);

        createParentDirectory(descriptorSetPath);
        try (var output = Files.newOutputStream(descriptorSetPath)) {
            descriptorSet.build().writeTo(output);
        }

        createParentDirectory(fileListPath);
        Files.writeString(fileListPath, String.join("\n", rootFiles) + "\n");

        System.out.printf(
                "Exported %d protocol files (%d descriptors including dependencies).%n",
                rootFiles.size(), descriptors.size());
    }

    private static List<Path> topLevelClassFiles(Path protocolClasses) throws IOException {
        try (Stream<Path> files = Files.walk(protocolClasses)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$"))
                    .sorted()
                    .toList();
        }
    }

    private static String toClassName(Path classesRoot, Path classFile) {
        var relative = classesRoot.relativize(classFile).toString();
        var withoutSuffix = relative.substring(0, relative.length() - ".class".length());
        return withoutSuffix.replace('\\', '.').replace('/', '.');
    }

    private static Method fileDescriptorMethod(Class<?> type) {
        try {
            var method = type.getDeclaredMethod("getDescriptor");
            if (Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0
                    && method.getReturnType() == FileDescriptor.class) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
            // Most generated message classes expose a message Descriptor instead of a FileDescriptor.
        }
        return null;
    }

    private static void collectDescriptor(
            FileDescriptor descriptor, Map<String, FileDescriptorProto> descriptors) {
        if (descriptors.containsKey(descriptor.getName())) {
            return;
        }

        for (var dependency : descriptor.getDependencies()) {
            collectDescriptor(dependency, descriptors);
        }
        descriptors.put(descriptor.getName(), descriptor.toProto());
    }

    private static void createParentDirectory(Path path) throws IOException {
        var parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String rootCauseMessage(Throwable error) {
        var cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        var message = cause.getMessage();
        return cause.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
