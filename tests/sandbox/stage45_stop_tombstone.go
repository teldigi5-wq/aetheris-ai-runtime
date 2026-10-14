// Stage 45.5.7: CI-only persistent worker STOP/deny record.
// The narrow writable mount survives container process restarts on ONE
// disposable CI host. Not protected against privileged host rollback.
package main

import (
 "errors"
 "fmt"
 "os"
 "path/filepath"
 "strconv"
 "syscall"
)

const stateDirectory = "/aetheris-state"
const tombstoneName = "terminal.tombstone"
const tombstoneVersion = "AETHERIS45TERMINALV1"

// Any state directory read error, symlink, corrupt marker or existing marker
// denies a new start, including old previously valid signed ALLOW leases.
func terminalPresent(dir string) bool {
 info, err := os.Lstat(dir)
 if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 { return true }
 path := filepath.Join(dir, tombstoneName)
 _, err = os.Lstat(path)
 return !os.IsNotExist(err)
}

func persistTerminal(dir, worker, job, reason string, epoch uint64) error {
 if reason != "STOP" && reason != "DENY" { return errors.New("invalid terminal reason") }
 info, err := os.Lstat(dir)
 if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
  return errors.New("unsafe worker-local state directory")
 }
 path := filepath.Join(dir, tombstoneName)
 // CREATE_NEW and NOFOLLOW: never overwrite a previous terminal decision.
 // Even a crash-truncated marker blocks any future start.
 fd, err := syscall.Open(path, syscall.O_CREAT|syscall.O_EXCL|
  syscall.O_WRONLY|syscall.O_NOFOLLOW|syscall.O_CLOEXEC, 0600)
 if err != nil { return fmt.Errorf("terminal marker refused: %w",err) }
 file := os.NewFile(uintptr(fd),path)
 evidence := tombstoneVersion+"|"+worker+"|"+job+"|"+strconv.FormatUint(epoch,10)+"|"+reason+"\n"
 _, writeErr := file.WriteString(evidence)
 if writeErr == nil { writeErr = file.Sync() }
 closeErr := file.Close()
 if writeErr != nil { return writeErr }
 if closeErr != nil { return closeErr }
 directory, err := os.Open(dir)
 if err != nil { return err }
 defer directory.Close()
 return directory.Sync()
}
