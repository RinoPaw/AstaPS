package emu.grasscutter.command;

/** Result of a completed command. Exit code zero means success. */
public record CommandResult(int exitCode) {
    public boolean succeeded() {
        return exitCode == 0;
    }
}
