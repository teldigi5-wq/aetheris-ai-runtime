// Stage 45.5.14 — Linux CI-only signing-key file custody checks.
// A file descriptor is opened with O_NOFOLLOW, then fstat'ed so a pathname
// swap cannot bypass the checked inode. Not a KMS, TPM or host-root barrier.
package main

import (
 "crypto/ed25519"
 "crypto/sha256"
 "crypto/subtle"
 "crypto/x509"
 "encoding/hex"
 "errors"
 "io"
 "os"
 "syscall"
)

func readBoundedKeyFile(path string, private bool) ([]byte,error) {
 fd,err:=syscall.Open(path,syscall.O_RDONLY|syscall.O_NOFOLLOW|syscall.O_CLOEXEC,0)
 if err!=nil {return nil,errors.New("key opening denied")}
 f:=os.NewFile(uintptr(fd),"sealed-ci-key")
 defer f.Close()
 var stat syscall.Stat_t
 if err:=syscall.Fstat(fd,&stat);err!=nil {
  return nil,errors.New("key inode unavailable")
 }
 if stat.Mode&syscall.S_IFMT!=syscall.S_IFREG || stat.Nlink!=1 ||
    stat.Size<=0 || stat.Size>512 {
  return nil,errors.New("key inode, link count or size denied")
 }
 if stat.Mode&0022!=0 {return nil,errors.New("writable key not trusted")}
 if private && (stat.Uid!=uint32(os.Geteuid()) || stat.Mode&0077!=0) {
  return nil,errors.New("private key owner or file mode denied")
 }
 b:=make([]byte,int(stat.Size))
 n,err:=io.ReadFull(f,b)
 if err!=nil || n!=len(b) {return nil,errors.New("key read incomplete")}
 return b,nil
}

func parsePinnedEdPublic(path,pinnedDigest string) (ed25519.PublicKey,error) {
 if len(pinnedDigest)!=64 {return nil,errors.New("public signing key pin absent")}
 expected,err:=hex.DecodeString(pinnedDigest)
 if err!=nil || len(expected)!=sha256.Size {return nil,errors.New("public signing key pin invalid")}
 der,err:=readBoundedKeyFile(path,false)
 if err!=nil {return nil,err}
 digest:=sha256.Sum256(der)
 if subtle.ConstantTimeCompare(expected,digest[:])!=1 {
  return nil,errors.New("unexpected signing identity")
 }
 parsed,err:=x509.ParsePKIXPublicKey(der)
 public,ok:=parsed.(ed25519.PublicKey)
 if err!=nil || !ok {return nil,errors.New("untrusted signing key type")}
 return public,nil
}

func parseOwnedEdPrivate(path string) (ed25519.PrivateKey,error) {
 der,err:=readBoundedKeyFile(path,true)
 if err!=nil {return nil,err}
 parsed,err:=x509.ParsePKCS8PrivateKey(der)
 key,ok:=parsed.(ed25519.PrivateKey)
 if err!=nil || !ok {return nil,errors.New("wrong CI IPC signing-key format")}
 // Parsed bytes remain process memory; CI uses a disposable key and no
 // production key persistence or export from this helper is authorized.
 return key,nil
}
