package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandResultTest {
    @Test
    void zeroIsTheOnlySuccessfulExitCode() {
        assertTrue(new CommandResult(0).succeeded());
        assertFalse(new CommandResult(1).succeeded());
        assertFalse(new CommandResult(2).succeeded());
        assertFalse(new CommandResult(7).succeeded());
        assertEquals(7, new CommandResult(7).exitCode());
    }
}
