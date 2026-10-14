package main

import (
 "crypto/ed25519"
 "crypto/rand"
 "encoding/hex"
 "fmt"
 "os"
 "path/filepath"
 "testing"
 "time"
)

func TestSignedLeaseRejectsTamperingExpiredAndCrossWorker(t *testing.T) {
 public,private,err := ed25519.GenerateKey(rand.Reader)
 if err != nil { t.Fatal(err) }
 file := filepath.Join(t.TempDir(),"lease")
 write := func(state string, epoch uint64, expiry int64, tamper bool) {
  t.Helper()
  body:= fmt.Sprintf("%s|workerFixture|11111111-1111-1111-1111-111111111111|%d|%d|%s|SLEEP\n",
   version,epoch,expiry,state)
  sig:= ed25519.Sign(private, []byte(body))
  if tamper { body = body[:len(body)-1] + "X" }
  if err:=os.WriteFile(file,[]byte(body+hex.EncodeToString(sig)+"\n"),0600);err!=nil {t.Fatal(err)}
 }
 job:="11111111-1111-1111-1111-111111111111"
 write("ALLOW",1,time.Now().Unix()+20,false)
 if grant,e:=verify(public,file,"workerFixture",job,1);e!=nil||grant.state!="ALLOW" {
  t.Fatalf("valid synthetic lease rejected: %v",e)
 }
 if _,e:=verify(public,file,"differentWorker",job,1);e==nil {t.Fatal("cross-worker lease accepted")}
 if _,e:=verify(public,file,"workerFixture","22222222-2222-2222-2222-222222222222",1);e==nil {t.Fatal("cross-job lease accepted")}
 write("ALLOW",1,time.Now().Unix()+20,true)
 if _,e:=verify(public,file,"workerFixture",job,1);e==nil {t.Fatal("tampered lease accepted")}
 write("ALLOW",1,time.Now().Unix()-1,false)
 if _,e:=verify(public,file,"workerFixture",job,1);e==nil {t.Fatal("expired lease accepted")}
 write("ALLOW",2,time.Now().Unix()+20,false)
 if _,e:=verify(public,file,"workerFixture",job,1);e==nil {t.Fatal("new epoch ALLOW accepted")}
 write("STOP",2,time.Now().Unix()+20,false)
 if grant,e:=verify(public,file,"workerFixture",job,1);e!=nil||grant.state!="STOP" {
  t.Fatalf("valid synthetic STOP lease rejected: %v",e)
 }
 write("STOP",1,time.Now().Unix()+20,false)
 if _,e:=verify(public,file,"workerFixture",job,1);e==nil {t.Fatal("same-epoch STOP accepted")}
}
