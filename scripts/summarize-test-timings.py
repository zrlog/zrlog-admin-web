#!/usr/bin/env python3
"""Report real Surefire durations, including on a failed run, from current reactor modules."""
import argparse
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--summary', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    modules = [node.text for node in ET.parse(root / 'pom.xml').findall('./m:modules/m:module', namespace)]
    suites, cases = [], []
    for module in modules:
        for path in sorted((root / module / 'target/surefire-reports').glob('TEST-*.xml')):
            suite = ET.parse(path).getroot()
            suites.append({'module': module, 'name': suite.attrib['name'],
                           'seconds': float(suite.attrib.get('time', 0)),
                           **{key: int(suite.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')}})
            for case in suite.findall('testcase'):
                cases.append({'module': module, 'class': case.attrib.get('classname', suite.attrib['name']),
                              'name': case.attrib['name'], 'seconds': float(case.attrib.get('time', 0)),
                              'status': next((kind for kind in ('failure', 'error', 'skipped') if case.find(kind) is not None), 'passed')})
    cases.sort(key=lambda case: case['seconds'], reverse=True)
    suites.sort(key=lambda suite: suite['seconds'], reverse=True)
    totals = {key: sum(suite[key] for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    report = {'sourceCommit': os.environ.get('GITHUB_SHA'), 'totals': totals, 'suites': suites, 'cases': cases,
              'note': 'Suite durations exclude JVM startup. Parallel suite times must not be summed as wall time; missing suites are not passes.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    if args.summary:
        def cell(text):
            return str(text).replace('|', '&#124;').replace('\n', ' ')
        lines = ['\n### Slowest test cases\n', '| Test | Seconds | Result |', '| --- | ---: | --- |']
        lines += [f"| {cell(case['class'])}#{cell(case['name'])} | {case['seconds']:.3f} | {case['status']} |" for case in cases[:20]]
        lines += ['', report['note'], '']
        with args.summary.open('a') as summary:
            summary.write('\n'.join(lines))
    print(json.dumps({'suites': len(suites), **totals}))


if __name__ == '__main__':
    main()
