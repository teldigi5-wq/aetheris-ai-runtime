package main

import (
 "crypto/ed25519"
 "crypto/rand"
 "crypto/sha256"
 "crypto/x509"
 "encoding/hex"
 "os"
 "path/filepath"
 "testing"
)

func makePublicKeyFile(t *testing.T,dir string) (string,string) {
 t.Helper()
 public,_,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 der,err:=x509.MarshalPKIXPublicKey(public)
 if err!=nil {t.Fatal(err)}
 path:=filepath.Join(dir,"issuer.der")
 if err:=os.WriteFile(path,der,0600);err!=nil {t.Fatal(err)}
 sum:=sha256.Sum256(der)
 return path,hex.EncodeToString(sum[:])
}

func TestPinnedPublicSignerForbidsSymlinkHardlinkAndSubstitution(t *testing.T) {
 dir:=t.TempDir()
 path,pin:=makePublicKeyFile(t,dir)
 if _,err:=parsePinnedEdPublic(path,pin);err!=nil {t.Fatal(err)}
 if _,err:=parsePinnedEdPublic(path,hex.EncodeToString(make([]byte,32)));err==nil {
  t.Fatal("substituted issuer key accepted")
 }
 if _,err:=parsePinnedEdPublic(path,"");err==nil {t.Fatal("missing key pin accepted")}
 symlink:=filepath.Join(dir,"link.der")
 if err:=os.Symlink(path,symlink);err!=nil {t.Fatal(err)}
 if _,err:=parsePinnedEdPublic(symlink,pin);err==nil {t.Fatal("symlinked key accepted")}
 hardlink:=filepath.Join(dir,"hard.der")
 if err:=os.Link(path,hardlink);err!=nil {t.Fatal(err)}
 if _,err:=parsePinnedEdPublic(path,pin);err==nil {t.Fatal("hardlinked signer key accepted")}
 if err:=os.Remove(hardlink);err!=nil {t.Fatal(err)}
 if err:=os.Chmod(path,0666);err!=nil {t.Fatal(err)}
 if _,err:=parsePinnedEdPublic(path,pin);err==nil {t.Fatal("world-writable signer key accepted")}
}

func TestOwnedPrivateKeyForbidsSymlinkHardlinkAndPermissiveMode(t *testing.T) {
 dir:=t.TempDir()
 _,private,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 der,err:=x509.MarshalPKCS8PrivateKey(private)
 if err!=nil {t.Fatal(err)}
 path:=filepath.Join(dir,"ipc-private.p8")
 if err:=os.WriteFile(path,der,0600);err!=nil {t.Fatal(err)}
 if _,err:=parseOwnedEdPrivate(path);err!=nil {t.Fatal(err)}
 if err:=os.Chmod(path,0644);err!=nil {t.Fatal(err)}
 if _,err:=parseOwnedEdPrivate(path);err==nil {t.Fatal("world-readable private key accepted")}
 if err:=os.Chmod(path,0600);err!=nil {t.Fatal(err)}
 alias:=filepath.Join(dir,"linked.p8")
 if err:=os.Link(path,alias);err!=nil {t.Fatal(err)}
 if _,err:=parseOwnedEdPrivate(path);err==nil {t.Fatal("hardlinked private key accepted")}
 if err:=os.Remove(alias);err!=nil {t.Fatal(err)}
 if err:=os.Symlink(path,alias);err!=nil {t.Fatal(err)}
 if _,err:=parseOwnedEdPrivate(alias);err==nil {t.Fatal("symlinked private key accepted")}
}

func TestSubstitutingValidEd25519PublicFileFailsPinnedIdentity(t *testing.T) {
 dir:=t.TempDir()
 original,pin:=makePublicKeyFile(t,dir)
 newPublic,_,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 substitute,err:=x509.MarshalPKIXPublicKey(newPublic)
 if err!=nil {t.Fatal(err)}
 // Simulate attacker replacing an otherwise well-formed Ed25519 file.
 if err:=os.WriteFile(original,substitute,0600);err!=nil {t.Fatal(err)}
 if _,err:=parsePinnedEdPublic(original,pin);err==nil {
  t.Fatal("valid substituted Ed25519 file changed the trusted identity")
 }
}
