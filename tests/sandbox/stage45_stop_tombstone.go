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

// A launch marker is written and fsynced BEFORE the fixed child is started.
// A crashed/killed worker must never accept a prior ALLOW on restart, even
// if STOP was not delivered and no terminal.tombstone yet exists.
const launchMarkerName = "launch.once"
const launchMarkerVersion = "AETHERIS45LAUNCHV1"

func restartDenied(dir string) bool {
 return terminalPresent(dir) || durableFileExists(dir,launchMarkerName)
}

func durableFileExists(dir, fileName string) bool {
 info, err := os.Lstat(dir)
 if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 { return true }
 _, err = os.Lstat(filepath.Join(dir,fileName))
 return !os.IsNotExist(err)
}

// oneShotLaunch persists a permanent fixture-local launch claim; any
// failure leaves the CI workload non-executable. This is not DB-global
// consensus and is not immune to rollback by a privileged host.
func oneShotLaunch(dir, worker, job string, epoch uint64) error {
 if terminalPresent(dir) { return errors.New("terminal stop already present") }
 data := launchMarkerVersion+"|"+worker+"|"+job+"|"+strconv.FormatUint(epoch,10)+"\\n"
 return createDurableOnce(dir,launchMarkerName,data)
}


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
 evidence := tombstoneVersion+"|"+worker+"|"+job+"|"+strconv.FormatUint(epoch,10)+"|"+reason+"\n"
 return createDurableOnce(dir,tombstoneName,evidence)
}

// All security-relevant records are created exactly once, without
// following symlinks; files and directory entries are synchronized.
func createDurableOnce(dir, filename, content string) error {
 info, err := os.Lstat(dir)
 if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
  return errors.New("unsafe worker-local state directory")
 }
 path := filepath.Join(dir,filename)
 fd, err := syscall.Open(path, syscall.O_CREAT|syscall.O_EXCL|
  syscall.O_WRONLY|syscall.O_NOFOLLOW|syscall.O_CLOEXEC, 0600)
 if err != nil { return fmt.Errorf("durable marker refused: %w",err) }
 file := os.NewFile(uintptr(fd),path)
 _, writeErr := file.WriteString(content)
 if writeErr == nil { writeErr = file.Sync() }
 closeErr := file.Close()
 if writeErr != nil { return writeErr }
 if closeErr != nil { return closeErr }
 directory, err := os.Open(dir)
 if err != nil { return err }
 defer directory.Close()
 return directory.Sync()
}
