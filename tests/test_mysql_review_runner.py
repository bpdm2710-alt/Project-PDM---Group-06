import unittest

from tools.run_mysql_review import EXPECTED, validate_output


class MysqlReviewRunnerTests(unittest.TestCase):
    def setUp(self):
        self.output = '\n'.join(f'PDM_TEST|{key}|{value}' for key, value in EXPECTED.items())
        self.errors = (
            "ERROR 1062 (23000) at line 1: Duplicate entry for key 'pdm_norm_old_select.PRIMARY'\n"
            "ERROR 1062 (23000) at line 2: Duplicate entry for key 'pdm_norm_new_select.PRIMARY'\n"
        )

    def test_accepts_exact_expected_outcomes(self):
        self.assertEqual(validate_output(self.output, self.errors, 0), EXPECTED)

    def test_rejects_unrelated_sql_failure(self):
        with self.assertRaises(AssertionError):
            validate_output(self.output, self.errors + 'ERROR 1452 unrelated FK failure\n', 0)

    def test_rejects_duplicate_errors_on_wrong_tables(self):
        with self.assertRaises(AssertionError):
            validate_output(self.output, self.errors.replace('pdm_norm_old_select', 'unrelated'), 0)

    def test_rejects_missing_result_even_when_client_returns_success(self):
        with self.assertRaises(AssertionError):
            validate_output('\n'.join(self.output.splitlines()[1:]), self.errors, 0)


if __name__ == '__main__':
    unittest.main()
