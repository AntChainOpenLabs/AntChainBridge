#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),assert=require('assert');
const [compilerModules,runtimeModules,output]=process.argv.slice(2);
if(!output)throw Error('usage: node test.cjs COMPILER_MODULES RUNTIME_MODULES NEW_OUTPUT');
const solc=require(path.join(compilerModules,'solc'));
const ganache=require(path.join(runtimeModules,'ganache'));
const ethers=require(path.join(runtimeModules,'ethers'));
const root=path.resolve(__dirname,'../../acb-sdk/pluginset/ethereum3/onchain-plugin/solidity/sys');
fs.mkdirSync(output);
async function main(){
 const source=fs.readFileSync(path.join(__dirname,'MonitorGasHarness.sol'),'utf8');
 const result=JSON.parse(solc.compile(JSON.stringify({language:'Solidity',sources:{'Fixture.sol':{content:source}},settings:{optimizer:{enabled:true,runs:200},outputSelection:{'*':{'*':['abi','evm.bytecode.object']}}}}),{import:f=>({contents:fs.readFileSync(path.resolve(root,f),'utf8')})}));
 const errors=(result.errors||[]).filter(e=>e.severity==='error');assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(output,'compiler-output.json'),JSON.stringify(result),{flag:'wx'});
 const chain=ganache.provider({logging:{quiet:true},chain:{hardfork:'london'},wallet:{totalAccounts:3}});
 try{
  const provider=new ethers.BrowserProvider(chain);provider.pollingInterval=20;
  const owner=await provider.getSigner(0),other=await provider.getSigner(1);
  async function deploy(n,args=[]){let c=result.contracts['Fixture.sol'][n];let x=await new ethers.ContractFactory(c.abi,c.evm.bytecode.object,owner).deploy(...args);await x.waitForDeployment();return x;}
  const decoder=await deploy('DecoderFixture'),verifier=await deploy('MonitorVerifierFixture'),hub=await deploy('HubFixture',[await owner.getAddress()]);
  await(await verifier.setPtcHubAddress(await hub.getAddress())).wait();
  await(await verifier.setMonitorAddress(await owner.getAddress())).wait();
  const signer=ethers.Wallet.createRandom();
  await(await verifier.seedNode('committee', '0x'+signer.signingKey.publicKey.slice(4))).wait();
  const results=[];
  for(const n of [0,1,2,31,32,33,36,63,64,65,127,128,129,255,256,257,511,512,513,8192]){
   const body=ethers.hexlify(Uint8Array.from({length:n},(_,i)=>(i*73+11)%256));
   assert.equal(await decoder.roundTrip(body),body);
   const [a,b]=await decoder.context('source',ethers.id('author'),ethers.id('receiver'),body);assert.equal(a,b);
   results.push({test:'byte-exact roundtrip and signed context',bytes:n,passed:true});
  }
  async function rejects(label,f){await assert.rejects(f);results.push({test:label,passed:true});}
  await rejects('short envelope rejects',()=>decoder.decode('0x1234'));
  const lane={channel:{senderDomain:'source',receiverDomain:'target'},senderId:ethers.ZeroHash,receiverId:ethers.ZeroHash};
  const data='0x010203040506';const context=ethers.id('current-message');
  const sig=signer.signingKey.sign(ethers.keccak256(data));
  const rawSig=ethers.concat([sig.r,sig.s,ethers.toBeHex(sig.yParity,1)]);
  const proof={nodeId:'monitor-node',signAlgo:'Keccak256WithSecp256k1',signature:rawSig};
  const receive=()=>verifier.receiveMonitorNodeProofMessage(lane,'committee',proof,data,context);
  await rejects('unauthorized result writer',()=>verifier.connect(other).receiveMonitorNodeProofMessage(lane,'committee',proof,data,context));
  await rejects('absent result rejects',()=>verifier.verifyMonitorNodeProofMessage.staticCall(context));
  await(await receive()).wait();
  assert.equal((await verifier.getPendingProofContext())[1],1n);
  await rejects('unauthorized consumer',()=>verifier.connect(other).verifyMonitorNodeProofMessage.staticCall(context));
  await rejects('different message cannot consume',()=>verifier.verifyMonitorNodeProofMessage.staticCall(ethers.id('wrong')));
  assert.equal(await verifier.verifyMonitorNodeProofMessage.staticCall(context),true);
  await(await verifier.verifyMonitorNodeProofMessage(context)).wait();
  await rejects('second consumption rejects',()=>verifier.verifyMonitorNodeProofMessage.staticCall(context));
  await(await verifier.receiveMonitorNodeProofMessage(lane,'committee',proof,'0xabcdef',context)).wait();
  assert.equal((await verifier.getPendingProofContext())[1],2n);
  assert.equal(await verifier.verifyMonitorNodeProofMessage.staticCall(context),false);
  await(await verifier.verifyMonitorNodeProofMessage(context)).wait();
  results.push({test:'invalid signature is a consumed false result, not a PTC rejection',passed:true});
  await rejects('missing monitor trust rejects',()=>verifier.receiveMonitorNodeProofMessage(lane,'unknown',proof,data,context));
  const monitor=await deploy('MonitorSourceFixture'),router=await deploy('RoutingFixture'),receiver=await deploy('RoutingFixture');
  await(await monitor.setProtocol(await router.getAddress())).wait();
  await(await monitor.setMonitorVerifier(await verifier.getAddress())).wait();
  await(await monitor.setMonitorControl(2)).wait();
  await(await verifier.setMonitorAddress(await monitor.getAddress())).wait();
  const author=ethers.id('author'), receiverId=ethers.zeroPadValue(await receiver.getAddress(),32), body='0x001122334455';
  await(await monitor.sendUnorderedMonitorMessage('target',receiverId,body)).wait();
  assert.equal(await decoder.decode(await router.lastMessage()),body);
  assert.equal(await router.lastAuthor(),await owner.getAddress());
  await(await monitor.seedSender(ethers.zeroPadValue(await owner.getAddress(),32),true)).wait();
  await rejects('sender blacklist still enforced',()=>monitor.sendUnorderedMonitorMessage.staticCall('target',receiverId,body));
  await(await monitor.seedSender(ethers.zeroPadValue(await owner.getAddress(),32),false)).wait();
  await(await monitor.seedReceiver(receiverId,true)).wait();
  await rejects('receiver blacklist still enforced',()=>monitor.sendUnorderedMonitorMessage.staticCall('target',receiverId,body));
  await(await monitor.seedReceiver(receiverId,false)).wait();
  const envelope=await decoder.encode(body);
  const binding=ethers.keccak256(ethers.AbiCoder.defaultAbiCoder().encode(['string','bytes32','bytes32','bytes32'],['source',author,receiverId,ethers.keccak256(envelope)]));
  await(await verifier.receiveMonitorNodeProofMessage(lane,'committee',proof,data,binding)).wait();
  await(await router.deliver(await monitor.getAddress(),'source',author,await receiver.getAddress(),envelope)).wait();
  assert.equal(await receiver.received(),1n);assert.equal(await receiver.lastMessage(),body);
  assert.equal((await verifier.getPendingProofContext())[1],0n);
  await rejects('business delivery cannot reuse proof',async()=>router.deliver.staticCall(await monitor.getAddress(),'source',author,await receiver.getAddress(),envelope));
  await(await verifier.receiveMonitorNodeProofMessage(lane,'committee',proof,'0xbad0',binding)).wait();
  await(await router.deliver(await monitor.getAddress(),'source',author,await receiver.getAddress(),envelope)).wait();
  assert.equal(await receiver.received(),1n);
  assert.equal((await verifier.getPendingProofContext())[1],0n);
  assert.notEqual(await router.lastMessage(),envelope);
  results.push({test:'valid delivery, consumed result and false-result rollback preserve behavior',passed:true});
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({passed:true,tests:results.length,results},null,2),{flag:'wx'});
  console.log(JSON.stringify({passed:true,tests:results.length}));
 }finally{await chain.disconnect();}
}
main().catch(e=>{console.error(e);process.exitCode=1});
