#!/usr/bin/env node
'use strict';
// Reuse the already installed compilers; outputs are exclusive experiment evidence.
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const [modules, output] = process.argv.slice(2);
if (!modules || !output) throw Error('usage: node compile.cjs NODE_MODULES NEW_OUTPUT');
const repo = path.resolve(__dirname, '../..');
fs.mkdirSync(output);
const variants = [
  ['evm', 'acb-sdk/pluginset/ethereum3/onchain-plugin/solidity/sys', require(path.join(modules, 'solc'))],
  ['mychain', 'acb-sdk/pluginset/mychain0.20/onchain-plugin/solidity/v1', require(path.join(modules, '@antchain/mysolidity/dist/utils.js')).getSolc('0.8.14')]
];
const manifest = [];
for (const [platform, relative, compiler] of variants) {
  const root = path.join(repo, relative);
  const names = ['Monitor', 'MonitorVerifier', 'CommitteePtcVerifier'];
  const sources = Object.fromEntries(names.map(n => [n + '.sol', {content: fs.readFileSync(path.join(root, n + '.sol'), 'utf8')}]));
  // Match the original deployments by default: FISCO uses the optimized EVM
  // build, while the pinned MySolidity CLI defaults to optimization disabled.
  const optimizer = {enabled: platform === 'evm' || process.argv.includes('--optimize-mychain'), runs: 200};
  const input = {language: 'Solidity', sources, settings: {optimizer, outputSelection: {'*': {'*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object', 'storageLayout']}}}};
  const result = JSON.parse(compiler.compile(JSON.stringify(input), {import: filename => {
    const p = path.resolve(root, filename);
    if (!p.startsWith(root + path.sep) || !fs.existsSync(p)) return {error: 'Unresolved import ' + filename};
    return {contents: fs.readFileSync(p, 'utf8')};
  }}));
  fs.writeFileSync(path.join(output, platform + '-input.json'), JSON.stringify(input), {flag: 'wx'});
  fs.writeFileSync(path.join(output, platform + '-output.json'), JSON.stringify(result), {flag: 'wx'});
  const errors = (result.errors || []).filter(x => x.severity === 'error');
  if (errors.length) throw Error(errors.map(x => x.formattedMessage).join('\n'));
  for (const name of names) {
    const c = result.contracts[name + '.sol'][name];
    for (const [ext, content] of [['abi.json', JSON.stringify(c.abi)], ['bin', c.evm.bytecode.object], ['runtime.bin', c.evm.deployedBytecode.object], ['storage.json', JSON.stringify(c.storageLayout)]]) {
      fs.writeFileSync(path.join(output, platform + '-' + name + '.' + ext), content, {flag: 'wx'});
    }
    manifest.push({platform, name, compiler: compiler.version(), optimizer, runtimeBytes: c.evm.deployedBytecode.object.length / 2,
      runtimeSha256: crypto.createHash('sha256').update(c.evm.deployedBytecode.object).digest('hex')});
  }
}
fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2), {flag: 'wx'});
console.log(JSON.stringify({passed: true, contracts: manifest}));
