#!/usr/bin/env python3
"""Read-only chain source provenance and sanitized transaction-tree evidence."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.request import Request, urlopen


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--rpc', required=True)
    p.add_argument('--dapp', required=True)
    p.add_argument('--source-root', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--transaction', action='append', default=[])
    p.add_argument('--plugin-log', type=Path)
    args = p.parse_args()

    def rpc(name, body):
        req = Request(args.rpc + '?req=' + name,
                      data=json.dumps(body).encode(),
                      headers={'Content-Type': 'application/json'})
        with urlopen(req, timeout=30) as response:
            value = json.load(response)
        if 'err' in value:
            raise RuntimeError(f'{name}: {value["err"]}')
        return value['ret']

    sources = []
    # AppContract (legacy) is embedded but intentionally not deployed.
    for path in sorted(args.source_root.rglob('*.gcl')):
        if path.name == 'AppContract.gcl':
            continue
        contract = args.dapp + '.' + path.stem
        actual = rpc('dx.source_code', {'contract': contract})
        if not isinstance(actual, str):
            raise RuntimeError(f'{contract}: unexpected source response type')
        expected = path.read_bytes()
        actual = actual.encode('utf-8')
        exact = actual == expected
        # Only CRLF/LF wrapping is permitted, never changes to GCL logic.
        newline_match = actual.replace(b'\r\n', b'\n').rstrip(b'\n') == expected.replace(b'\r\n', b'\n').rstrip(b'\n')
        row = {'source': str(path.relative_to(args.source_root)),
               'contract': contract,
               'sourceSha256': hashlib.sha256(expected).hexdigest(),
               'chainSha256': hashlib.sha256(actual).hexdigest(),
               'exactMatch': exact, 'newlineOnlyMatch': newline_match}
        sources.append(row)
        print(f'{contract}: exact={exact}, newlineOnly={newline_match}', flush=True)
        if not newline_match:
            raise RuntimeError(f'{contract}: chain source differs from specified source')

    rows = {}
    def visit(reference):
        if reference in rows:
            return
        base, _, index = reference.partition(':')
        tx = rpc('dx.transaction', {'hash': base})
        group_state = tx.get('State')
        group_height = tx.get('Height')
        if index:
            embedded = tx.get('Relays', [])
            tx = embedded[int(index)]
        invocation = tx.get('Invocation') or {}
        refs = invocation.get('Relays', [])
        row = {'reference': reference, 'hash': base, 'height': tx.get('Height', group_height),
               'state': group_state, 'function': tx.get('Function'),
               'target': tx.get('Target'), 'status': invocation.get('Status'),
               'errorCode': invocation.get('ErrorCode'), 'relays': refs}
        rows[reference] = row
        if invocation.get('Status') not in (None, 'IVKRET_SUCCESS'):
            raise RuntimeError(f'{reference}: execution not successful')
        if invocation.get('ErrorCode') not in (None, 'NoException'):
            raise RuntimeError(f'{reference}: execution exception')
        if group_state != 'DUS_ARCHIVED':
            raise RuntimeError(f'{reference}: transaction not archived')
        for child in refs:
            visit(child)
        if not index:
            for i, embedded in enumerate(tx.get('Relays') or []):
                if isinstance(embedded, dict):
                    visit(f'{base}:{i}')

    roots = list(args.transaction)
    if args.plugin_log:
        roots += re.findall(r'Dioxide submission accepted: .*?hash=([0-9a-z]+)',
                            args.plugin_log.read_text(encoding='utf-8'))
    for root in dict.fromkeys(roots):
        visit(root)
    contracts = {}
    fields = {'owner', 'relayer', 'protocolIDs', 'protocolRoutes',
              'ptcHubId', 'ptcHubAddress', 'amContractId', 'amAddress',
              'localDomain', 'monitorContractId', 'monitorAddress',
              'sdpContractId', 'sdpAddress', 'monitorVerifierId',
              'monitorVerifierAddress', 'monitorControl', 'authMessageAddress'}
    for name in ('AuthMsg', 'SDPMsg', 'MonitorVerifier', 'Monitor', 'PtcHub', 'AppContractV2'):
        contract = args.dapp + '.' + name
        info = rpc('dx.contract_info', {'contract': contract})
        state = rpc('dx.contract_state', {'contract_with_scope': contract + '.global'})['State']
        contracts[name] = {'cid': info['ContractVersionID'],
                           'identity': f'{int(info["ContractVersionID"]):064x}',
                           'state': {k: v for k, v in state.items() if k in fields}}
    report = {'dapp': args.dapp, 'sourceCount': len(sources),
              'allSourceMatches': True, 'sources': sources,
              'rootTransactions': list(dict.fromkeys(roots)),
              'contracts': contracts,
              'transactionCount': len(rows), 'transactions': list(rows.values())}
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    args.output.chmod(0o600)
    print(f'sourceCount={len(sources)}, transactionCount={len(rows)}, allChecksPassed=true')


if __name__ == '__main__':
    main()
