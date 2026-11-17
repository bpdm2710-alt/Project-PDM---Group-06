"""Small exhaustive FD checker for the project's teaching-sized relations.

No database connection and no third-party dependency. FDs are assumptions supplied
by the team; passing this program cannot establish their business correctness.
"""

from __future__ import annotations

import argparse
import itertools
import json
from pathlib import Path

FD = tuple[frozenset[str], str]
MAX_ATTRIBUTES = 12


def closure(attributes, fds):
    result = set(attributes)
    changed = True
    while changed:
        changed = False
        for left, right in fds:
            if left <= result and right not in result:
                result.add(right)
                changed = True
    return frozenset(result)


def subsets(attributes):
    ordered = sorted(attributes)
    for size in range(len(ordered) + 1):
        for combination in itertools.combinations(ordered, size):
            yield frozenset(combination)


def fd_order(fd):
    return (len(fd[0]), tuple(sorted(fd[0])), fd[1])


def parse_fds(entries):
    return sorted({(frozenset(e['lhs']), right)
                   for e in entries for right in e['rhs']}, key=fd_order)


def equivalent(first, second):
    return (all(right in closure(left, second) for left, right in first)
            and all(right in closure(left, first) for left, right in second))


def minimal_cover(fds):
    current = sorted(set(fds), key=fd_order)
    changed = True
    while changed:
        changed = False
        for i, (left, right) in enumerate(current):
            for attribute in sorted(left):
                reduced = left - {attribute}
                if right in closure(reduced, current):
                    current[i] = (reduced, right)
                    changed = True
                    break
            if changed:
                break
    index = 0
    while index < len(current):
        left, right = current[index]
        others = current[:index] + current[index + 1:]
        if right in closure(left, others):
            current.pop(index)
        else:
            index += 1
    return sorted(set(current), key=fd_order)


def candidate_keys(attributes, fds):
    attributes = frozenset(attributes)
    mandatory = attributes - {right for _, right in fds}
    keys = []
    for extra in subsets(attributes - mandatory):
        trial = mandatory | extra
        if any(key <= trial for key in keys):
            continue
        if closure(trial, fds) >= attributes:
            keys.append(trial)
    return sorted(keys, key=lambda k: (len(k), tuple(sorted(k))))


def normal_form(attributes, fds):
    """Check all determinants in F+, assuming atomic domains (1NF).

    Checking the full closure avoids treating a convenient list of given FDs as
    if it were all dependencies. NULL semantics are outside this classical model.
    """
    attributes = frozenset(attributes)
    keys = candidate_keys(attributes, fds)
    prime = frozenset().union(*keys)
    witnesses = {'2NF': [], '3NF': [], 'BCNF': []}
    for left in subsets(attributes):
        determined = closure(left, fds) & attributes
        if determined >= attributes:
            continue
        for right in sorted(determined - left):
            witnesses['BCNF'].append((left, right))
            if right not in prime:
                witnesses['3NF'].append((left, right))
                if any(left < key for key in keys):
                    witnesses['2NF'].append((left, right))
    level = ('1NF' if witnesses['2NF'] else
             '2NF' if witnesses['3NF'] else
             '3NF' if witnesses['BCNF'] else 'BCNF')
    return level, witnesses


def lossless_binary(attributes, first, second, fds):
    attributes, first, second = map(frozenset, (attributes, first, second))
    if first | second != attributes:
        return False
    common_closure = closure(first & second, fds)
    return first <= common_closure or second <= common_closure


def project_fds(attributes, fds):
    attributes = frozenset(attributes)
    projected = [(left, right) for left in subsets(attributes)
                 for right in sorted((closure(left, fds) & attributes) - left)]
    return minimal_cover(projected)


def preserves_dependencies(components, fds):
    projected = [fd for component in components for fd in project_fds(component, fds)]
    return all(right in closure(left, projected) for left, right in fds)


def validate_spec(spec):
    records = spec.get('relations', [])
    names = [r['name'] for r in records]
    if len(set(names)) != len(names):
        raise ValueError('Duplicate relation name')
    results = []
    for relation in records:
        attrs = frozenset(relation['attributes'])
        if not attrs or len(attrs) != len(relation['attributes']):
            raise ValueError('Empty or duplicate attributes: ' + relation['name'])
        if len(attrs) > MAX_ATTRIBUTES:
            raise ValueError('Relation exceeds exhaustive-check limit: ' + relation['name'])
        fds = parse_fds(relation['fds'])
        if any(not left <= attrs or right not in attrs for left, right in fds):
            raise ValueError('FD uses an unknown attribute: ' + relation['name'])
        cover = minimal_cover(fds)
        if not equivalent(fds, cover):
            raise AssertionError('Cover is not equivalent: ' + relation['name'])
        for i, (left, right) in enumerate(cover):
            if right in closure(left, cover[:i] + cover[i + 1:]):
                raise AssertionError('Redundant FD: ' + relation['name'])
            if any(right in closure(left - {a}, cover) for a in left):
                raise AssertionError('Extraneous LHS attribute: ' + relation['name'])
        keys = candidate_keys(attrs, fds)
        expected = {frozenset(k) for k in relation['expected_keys']}
        if set(keys) != expected:
            raise AssertionError('Candidate-key mismatch: ' + relation['name'])
        level, witnesses = normal_form(attrs, fds)
        if level != relation['expected_normal_form']:
            raise AssertionError('Normal-form mismatch: ' + relation['name'])
        results.append({
            'name': relation['name'], 'normal_form': level,
            'keys': [sorted(k) for k in keys],
            'minimal_cover': [{'lhs': sorted(left), 'rhs': [right]} for left, right in cover],
            'first_violation': next(({'form': form, 'lhs': sorted(v[0][0]), 'rhs': v[0][1]}
                                     for form, v in witnesses.items() if v), None),
        })
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    default = Path(__file__).resolve().parents[1] / 'docs/normalization/fd-spec.json'
    parser.add_argument('--spec', type=Path, default=default)
    parser.add_argument('--json', action='store_true', help='Print computed proofs as JSON')
    args = parser.parse_args()
    records = validate_spec(json.loads(args.spec.read_text(encoding='utf-8')))
    if args.json:
        print(json.dumps(records, ensure_ascii=True, indent=2))
    else:
        for record in records:
            keys = '; '.join('{' + ', '.join(k) + '}' for k in record['keys'])
            print(f"{record['name']}: {record['normal_form']}; keys={keys}")
        print(f'PASS: {len(records)} relation scenarios; covers, all keys and normal forms checked.')
        print('Conditional on the declared business rules; not a database enforcement test.')


if __name__ == '__main__':
    main()
