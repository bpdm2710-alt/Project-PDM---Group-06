"""Run the temporary-table SQL review lab, with exact expected-error checking.

Requires a MySQL client and an existing scratch database. It does not create or
drop databases. Credentials can be provided through a configured login path.
Never pass passwords in arguments or commit a credentials file.
"""

import argparse
from pathlib import Path
import re
import subprocess
import sys

EXPECTED = {
    'old_key_rejects_valid': 1,
    'old_key_accepts_two_options': 2,
    'new_key_accepts_two_questions': 2,
    'new_key_rejects_second_choice': 2,
    'both_refs_exist_but_wrong_exam': 1,
    'registration_has_two_exam_values': 1,
    'rejoined_rows': 4,
    'missing_after_rejoin': 0,
    'spurious_after_rejoin': 0,
}


def validate_output(stdout, stderr, returncode):
    lines = [line for line in stdout.splitlines() if line.startswith('PDM_TEST|')]
    found = {}
    for line in lines:
        _, name, value = line.split('|')
        if name in found:
            raise AssertionError('Duplicate result: ' + name)
        found[name] = int(value)
    errors = re.findall(r'ERROR\s+(\d+)', stderr)
    if returncode != 0 or errors != ['1062', '1062'] or found != EXPECTED:
        raise AssertionError(f'Unexpected SQL outcome: returncode={returncode}, errors={errors}, results={found}')
    # Verify the deliberate failures happened in the intended sequence/tables.
    error_lines = [line for line in stderr.splitlines() if 'ERROR ' in line]
    if 'pdm_norm_old_select' not in error_lines[0] or 'pdm_norm_new_select' not in error_lines[1]:
        raise AssertionError('Duplicate errors did not come from the two intended tables')
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', default='mysql', help='Path to mysql executable')
    parser.add_argument('--database', required=True, help='Existing scratch database')
    parser.add_argument('--login-path', help='Existing mysql_config_editor login path')
    parser.add_argument('--host', default=None)
    parser.add_argument('--port', type=int, default=None)
    parser.add_argument('--user', default=None)
    parser.add_argument('--protocol', choices=['TCP', 'SOCKET', 'PIPE', 'MEMORY'])
    parser.add_argument('--shared-memory-base-name', default=None)
    args = parser.parse_args()
    command = [args.mysql]
    if args.login_path:
        command.append('--login-path=' + args.login_path)
    for option in ['host', 'port', 'user']:
        value = getattr(args, option)
        if value is not None:
            command.append('--' + option + '=' + str(value))
    if args.protocol:
        command.append('--protocol=' + args.protocol)
    if args.shared_memory_base_name:
        command.append('--shared-memory-base-name=' + args.shared_memory_base_name)
    command += ['--batch', '--raw', '--skip-column-names', '--force', '--database=' + args.database]
    script = Path(__file__).resolve().parents[1] / 'database/review/normalization_counterexamples.sql'
    process = subprocess.run(command, input=script.read_text(encoding='utf-8'),
                             text=True, capture_output=True, timeout=90)
    try:
        results = validate_output(process.stdout, process.stderr, process.returncode)
    except (AssertionError, ValueError) as exc:
        print(process.stdout, file=sys.stderr)
        print(process.stderr, file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 1
    print(process.stdout.rstrip())
    print(f'PASS: {len(results)} SQL outcomes and exactly two intended duplicate-key errors.')
    print('No persistent project tables were modified; this is a limited review lab.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
