import json
from pathlib import Path
import unittest

from tools.normalization_check import (
    candidate_keys, closure, equivalent, lossless_binary, minimal_cover,
    normal_form, parse_fds, preserves_dependencies, validate_spec,
)


def fd(left, right):
    return (frozenset(left.split()), right)


class NormalizationTests(unittest.TestCase):
    def test_transitive_closure(self):
        self.assertEqual(closure({'A'}, [fd('A', 'B'), fd('B', 'C')]), {'A', 'B', 'C'})

    def test_cover_removes_extraneous_and_redundant_dependencies(self):
        original = [fd('A', 'B'), fd('A B', 'C'), fd('A', 'C'), fd('C', 'D')]
        cover = minimal_cover(original)
        self.assertTrue(equivalent(original, cover))
        self.assertEqual(set(cover), {fd('A', 'B'), fd('A', 'C'), fd('C', 'D')})

    def test_alternate_keys(self):
        fds = [fd('A B', 'C'), fd('C', 'B')]
        self.assertEqual(set(candidate_keys({'A', 'B', 'C'}, fds)),
                         {frozenset({'A', 'B'}), frozenset({'A', 'C'})})

    def test_3nf_is_not_necessarily_bcnf(self):
        self.assertEqual(normal_form({'A', 'B', 'C'}, [fd('A B', 'C'), fd('C', 'B')])[0], '3NF')

    def test_transitive_dependency_is_2nf(self):
        self.assertEqual(normal_form({'A', 'B', 'C'}, [fd('A', 'B'), fd('B', 'C')])[0], '2NF')

    def test_partial_dependency_is_1nf(self):
        self.assertEqual(normal_form({'A', 'B', 'C'}, [fd('A', 'C')])[0], '1NF')

    def test_all_key_relation(self):
        self.assertEqual(candidate_keys({'A', 'B'}, []), [frozenset({'A', 'B'})])
        self.assertEqual(normal_form({'A', 'B'}, [])[0], 'BCNF')

    def test_empty_determinant(self):
        fds = [fd('', 'B'), fd('A', 'C')]
        self.assertEqual(closure(set(), fds), {'B'})
        self.assertEqual(candidate_keys({'A', 'B', 'C'}, fds), [frozenset({'A'})])

    def test_attempt_decomposition(self):
        attrs = {'Attempt_no', 'RegisID', 'Exam_ID', 'PaperID', 'StartTime', 'EndTime'}
        fds = [fd('RegisID', 'Exam_ID')] + [fd('Attempt_no RegisID', r)
                for r in ['PaperID', 'StartTime', 'EndTime']]
        components = [{'RegisID', 'Exam_ID'}, attrs - {'Exam_ID'}]
        self.assertTrue(lossless_binary(attrs, *components, fds))
        self.assertTrue(preserves_dependencies(components, fds))
        for component in components:
            from tools.normalization_check import project_fds
            self.assertEqual(normal_form(component, project_fds(component, fds))[0], 'BCNF')

    def test_exam_level_marks_decomposition(self):
        attrs = {'Exam_ID', 'PaperID', 'Quest_ID', 'QuestionOrder', 'Marks'}
        fds = [fd('Exam_ID PaperID Quest_ID', 'QuestionOrder'),
               fd('Exam_ID PaperID QuestionOrder', 'Quest_ID'), fd('Exam_ID Quest_ID', 'Marks')]
        components = [{'Exam_ID', 'Quest_ID', 'Marks'}, attrs - {'Marks'}]
        self.assertTrue(lossless_binary(attrs, *components, fds))
        self.assertTrue(preserves_dependencies(components, fds))

    def test_lossy_decomposition_detected(self):
        self.assertFalse(lossless_binary({'A', 'B', 'C'}, {'A', 'B'}, {'B', 'C'}, [fd('A', 'C')]))

    def test_lossless_can_lose_dependencies(self):
        fds = [fd('A B', 'C'), fd('C', 'B')]
        components = [{'A', 'C'}, {'B', 'C'}]
        self.assertTrue(lossless_binary({'A', 'B', 'C'}, *components, fds))
        self.assertFalse(preserves_dependencies(components, fds))

    def test_full_project_spec(self):
        path = Path(__file__).resolve().parents[1] / 'docs/normalization/fd-spec.json'
        records = validate_spec(json.loads(path.read_text(encoding='utf-8')))
        self.assertEqual(sum(r['name'].startswith('proposed.') for r in records), 20)
        self.assertTrue(all(r['normal_form'] == 'BCNF' for r in records if r['name'].startswith('proposed.')))

    def test_invalid_attribute_rejected(self):
        spec = {'relations': [{'name': 'bad', 'attributes': ['A'],
                               'fds': [{'lhs': ['B'], 'rhs': ['A']}],
                               'expected_keys': [['A']], 'expected_normal_form': 'BCNF'}]}
        with self.assertRaises(ValueError):
            validate_spec(spec)


if __name__ == '__main__':
    unittest.main()
