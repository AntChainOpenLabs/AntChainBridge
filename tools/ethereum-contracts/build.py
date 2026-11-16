#!/usr/bin/env python3
"""Build pinned Ethereum system contracts; never installs or connects to a chain."""
import argparse, hashlib, json, pathlib, subprocess

VERSION = '0.8.29+commit.ab55807c'
COMPILER_SHA256 = '18d418a40dc04d17656b1b5c8a7b35cfbab8942b51f38d005d5b59e8aa6637e0'
SETTINGS = {'optimizer': {'enabled': True, 'runs': 200}, 'viaIR': True,
            'evmVersion': 'london', 'metadata': {'bytecodeHash': 'ipfs'}}
CONTRACTS = ['AuthMsg', 'SDPMsg', 'PtcHub', 'CommitteePtcVerifier', 'AppContract',
             'ProxyAdmin', 'TransparentUpgradeableProxy']

def build(compiler, product, output):
    if hashlib.sha256(compiler.read_bytes()).hexdigest() != COMPILER_SHA256:
        raise ValueError('Compiler binary checksum differs from official Linux amd64 release')
    version = subprocess.check_output([str(compiler), '--version'], text=True)
    if VERSION not in version:
        raise ValueError('Expected official standard solc ' + VERSION)
    repo = pathlib.Path(__file__).resolve().parents[2]
    source_root = repo / 'acb-sdk/pluginset' / product / 'onchain-plugin/solidity/sys'
    sources = {str(p.relative_to(source_root)): {'content': p.read_text()}
               for p in sorted(source_root.rglob('*.sol'))}
    settings = dict(SETTINGS, outputSelection={'*': {'*': [
        'abi', 'evm.bytecode.object', 'evm.deployedBytecode.object', 'storageLayout']}})
    output.mkdir(parents=True, exist_ok=False)
    request = {'language': 'Solidity', 'sources': sources, 'settings': settings}
    with (output / 'input.json').open('x') as f:
        json.dump(request, f)
    with (output / 'input.json').open('r') as inp, (output / 'output.json').open('x') as out:
        subprocess.run([str(compiler), '--standard-json'], stdin=inp, stdout=out, check=True)
    result = json.loads((output / 'output.json').read_text())
    errors = [e['formattedMessage'] for e in result.get('errors', []) if e['severity'] == 'error']
    if errors:
        raise ValueError('\n'.join(errors))
    names = CONTRACTS + (['Monitor', 'MonitorVerifier'] if product == 'ethereum3' else [])
    manifest = {'compiler': VERSION, 'compilerSha256': hashlib.sha256(compiler.read_bytes()).hexdigest(),
                'settings': SETTINGS, 'product': product, 'sources': {
                    name: hashlib.sha256(x['content'].encode()).hexdigest() for name, x in sources.items()},
                'contracts': {}}
    for name in names:
        candidates = [(p, cs[name]) for p, cs in result['contracts'].items() if name in cs]
        if len(candidates) > 1:
            candidates = [(path, value) for path, value in candidates if path == name + '.sol']
        if len(candidates) != 1:
            raise ValueError('Ambiguous/missing contract: ' + name)
        path, c = candidates[0]
        runtime = c['evm']['deployedBytecode']['object']
        if len(runtime) // 2 > 24576:
            raise ValueError('EIP-170 limit exceeded: ' + name)
        (output / (name + '.abi')).write_text(json.dumps(c['abi']))
        (output / (name + '.bin')).write_text(c['evm']['bytecode']['object'])
        (output / (name + '.runtime')).write_text(runtime)
        manifest['contracts'][name] = {'source': path, 'runtimeBytes': len(runtime) // 2,
                                       'creationSha256': hashlib.sha256(bytes.fromhex(c['evm']['bytecode']['object'])).hexdigest()}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(product, {k: v['runtimeBytes'] for k, v in manifest['contracts'].items()}, flush=True)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--compiler', required=True, type=pathlib.Path)
    p.add_argument('--product', required=True, choices=['ethereum2', 'ethereum3'])
    p.add_argument('--output', required=True, type=pathlib.Path)
    a = p.parse_args()
    build(a.compiler, a.product, a.output)
