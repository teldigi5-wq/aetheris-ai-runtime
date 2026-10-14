package main

import (
 "crypto/ed25519"
 "crypto/rand"
 "fmt"
 "strings"
 "testing"
 "time"
)

const fixedJob="11111111-1111-1111-1111-111111111111"
const fixedWorker="syntheticWorker"

func TestPinnedBrokerRequestVerifiesSignatureScopeAndShortDeadline(t *testing.T) {
 pub,priv,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 badPub,_,_:=ed25519.GenerateKey(rand.Reader)
 container:=strings.Repeat("a",64)
 now:=time.Now().Unix()
 request:=stopRequest{fixedWorker,fixedJob,container,"SIGNED_STOP",1,now}
 frame:=signedStopRequest(priv,request)
 if err:=verifyStopRequest(pub,frame,fixedWorker,fixedJob,container,1,now);err!=nil {t.Fatal(err)}
 for _,tc:=range []struct{w,j,c string;e uint64;at int64;p ed25519.PublicKey}{
  {"wrongWorker",fixedJob,container,1,now,pub},
  {fixedWorker,"22222222-2222-2222-2222-222222222222",container,1,now,pub},
  {fixedWorker,fixedJob,strings.Repeat("b",64),1,now,pub},
  {fixedWorker,fixedJob,container,2,now,pub},
  {fixedWorker,fixedJob,container,1,now+7,pub},
  {fixedWorker,fixedJob,container,1,now,badPub},
 }{
  if err:=verifyStopRequest(tc.p,frame,tc.w,tc.j,tc.c,tc.e,tc.at);err==nil{
   t.Fatal("re-targeted or expired stop accepted")
  }
 }
 modified:=append([]byte(nil),frame...);modified[12]^=1
 if err:=verifyStopRequest(pub,modified,fixedWorker,fixedJob,container,1,now);err==nil{
  t.Fatal("tampered stop signature accepted")
 }
}

func TestVerifierProtocolRejectsForgedOrReplayedHeartbeat(t *testing.T) {
 pub,priv,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 container:=strings.Repeat("b",64)
 now:=time.Now().Unix()
 payload:=fmt.Sprintf("%s|%s|%s|%s|1|3|%d|%d|ACTIVE\n",
  heartbeatDomain,fixedWorker,fixedJob,container,now,now+3)
 frame:=[]byte(payload+fmt.Sprintf("%x",ed25519.Sign(priv,[]byte(payload)))+"\n")
 if p,e:=verifySignedHeartbeat(pub,frame,fixedWorker,fixedJob,container,1,2,now);e!=nil||p.sequence!=3{
  t.Fatalf("valid synthetic heartbeat denied: %v",e)
 }
 if _,e:=verifySignedHeartbeat(pub,frame,fixedWorker,fixedJob,container,1,4,now);e==nil {
  t.Fatal("older signed heartbeat accepted")
 }
 if _,e:=verifySignedHeartbeat(pub,frame,fixedWorker,fixedJob,container,1,2,now+4);e==nil{
  t.Fatal("expired pulse accepted")
 }
}
