-- ============================================================================
-- 05_upgrade_password_hashes.sql
-- For a database that was built from the OLD 02 seed (Password_hash = 'sha256:...').
-- Replaces those placeholder values with real PBKDF2 hashes (same as the new 02 seed).
-- Demo passwords afterwards: instructors (1-15) Inst@123, students (101-120) Stu@123.
-- Only rows still holding the old placeholder are touched, so it is safe to run twice.
-- (Even without this script, the app upgrades an old value to a hash on the first login
--  that types the old value exactly, e.g. 'sha256:stu_hash_101'.)
-- ============================================================================
USE `Online_Examination`;
START TRANSACTION;
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$Og2qQb+GkDqaKFjiefizrQ$5ilrT0ZBviA3IVMIbKOuARdKL8Pvr9KGiFFUn6JSDCE' WHERE `User_ID` = 1 AND `Password_hash` = 'sha256:inst_hash_01';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$YRn25lQrjSzX3iCdWrI2Zw$JJb1Tf7S6Q6VYV5HvpMQN0yNlYSU/2/qBq/Z2xYompM' WHERE `User_ID` = 2 AND `Password_hash` = 'sha256:inst_hash_02';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$HYtErnCWBu1YBjeBLTiwcg$La+5oOYtiEX6DQD32KYGje2rDxVZVdvQTQ3PEWDwe1k' WHERE `User_ID` = 3 AND `Password_hash` = 'sha256:inst_hash_03';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$QN4/s7hrchjcKFCx6W7EaQ$EHWBIXpIf4LBwO8cFR9P+fKbkGlXOIyWlnyLmScZuO8' WHERE `User_ID` = 4 AND `Password_hash` = 'sha256:inst_hash_04';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$8XzgLgUq5BYQQTgfYik/0w$mMmY1a02uIzXq5mCOgZTwGXBoJLEKjwDTBriNWos6YQ' WHERE `User_ID` = 5 AND `Password_hash` = 'sha256:inst_hash_05';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$CYtR0/1E4glGpoEDEaXMew$IlZoj8quLNCDU2SUa7XcsEgJp7RHihwpLhNa+XU2zXc' WHERE `User_ID` = 6 AND `Password_hash` = 'sha256:inst_hash_06';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$ejKwPu0+z8TxAmM8DnfOog$evUUFhD8ryffiQL7zzLamXxhxdsfpoMgE8yICBy8DN8' WHERE `User_ID` = 7 AND `Password_hash` = 'sha256:inst_hash_07';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$IigjktsnEOQhI9U3tyG1iw$7C8ol9/bEcmhJl+cdUVx7JecamX3CevqwHmyt1LR2d8' WHERE `User_ID` = 8 AND `Password_hash` = 'sha256:inst_hash_08';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$R0er0r1l4sdoLR21tZioJA$M1NOZg2kdP+/ImhqLfzRqTJLHXHhnJxpwOl3cxzpoL4' WHERE `User_ID` = 9 AND `Password_hash` = 'sha256:inst_hash_09';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$ftD/w594yAD+2uEVa8RCnA$/XORLHpocYAnTfVRx3ukOJZdYuWLfiL3fuToqLr/goU' WHERE `User_ID` = 10 AND `Password_hash` = 'sha256:inst_hash_10';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$53XXrT1fiIHeo0bPg5dK2w$I1zsmdak1jz8XRRGYEN5BSqJU/vJ/iY2upt2KYsAEcw' WHERE `User_ID` = 11 AND `Password_hash` = 'sha256:inst_hash_11';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$Sgi82GrUrw7BDCm2l+touA$RYAowJYT6LzuWhQi+1L7lr06jiN+HcjsjfEhvH72BPg' WHERE `User_ID` = 12 AND `Password_hash` = 'sha256:inst_hash_12';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$TqfYFcK/olDCHGIqNc4PPA$wbdu2PEeU5OWoP6Gbd01dukznZ9XuF802FKbhzL+noY' WHERE `User_ID` = 13 AND `Password_hash` = 'sha256:inst_hash_13';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$LhLJKAHZ/9cFj0HzcxWjKQ$N1CIOAJPg1poqOeAkaEHO4ZZ91kmCDt4vV85mSOfnqA' WHERE `User_ID` = 14 AND `Password_hash` = 'sha256:inst_hash_14';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$ftexQF/VJvvX3p3/YvayjA$DUDETXejnF9YzxvTwSOMe7aVu5RcYMq2Zqpj9SzLnUs' WHERE `User_ID` = 15 AND `Password_hash` = 'sha256:inst_hash_15';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$9y6gQoCXufMytg/XPDWt4w$bUmOAoX9kxverWHqOohBs6RneQlEtOPGL/ccCdatolU' WHERE `User_ID` = 101 AND `Password_hash` = 'sha256:stu_hash_101';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$UIAIjeNjrjDjbIohX+LFaw$8MpgQ8GvMxpHo3rRdWSGBZtQKbh3VV+ulsViOTgsZpo' WHERE `User_ID` = 102 AND `Password_hash` = 'sha256:stu_hash_102';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$kQ+C4hmPJjWlYVRH3BHwmg$nw1cb56jlUliFmmWHVreU5CG3QD8k2pxd3k9GLxhZrA' WHERE `User_ID` = 103 AND `Password_hash` = 'sha256:stu_hash_103';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$SDEuDIFCFFOFJle6Yy6UZA$oxb8Mhe/EoT5dQyFQxJv/hSDatYuwqVW/3HtgUErzRk' WHERE `User_ID` = 104 AND `Password_hash` = 'sha256:stu_hash_104';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$0/o9qvLzq2duWh/VsX6M6w$97Wk2EPE+DIO2eZ0hG6tHKibuUqZO+ofKwp/Cx5UXS4' WHERE `User_ID` = 105 AND `Password_hash` = 'sha256:stu_hash_105';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$SCKx1M4wKX6tXTYhxYaAVQ$HYdhtZPvkrR1+P9D4RfuF+Oqb2b4nSY6d78mYvfBA04' WHERE `User_ID` = 106 AND `Password_hash` = 'sha256:stu_hash_106';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$HX3+1Oz7dlGsyABWgh5H+w$hQ6Bj1eiG5rPbro20Unt+HzwCSWnB9UqaDGz5p/ehng' WHERE `User_ID` = 107 AND `Password_hash` = 'sha256:stu_hash_107';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$BXx3lDlL/uJyzeIldW5QJA$U7ge93LOT9py6+xt9xpw+FSn9Syz3i2sYiYHVJ5RNS4' WHERE `User_ID` = 108 AND `Password_hash` = 'sha256:stu_hash_108';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$w9K1FPZdqHbTpEWCv7nx7A$QKL0/FySG4hgGBMSUOlybNXzG1/FgnCEiSR7VML25nw' WHERE `User_ID` = 109 AND `Password_hash` = 'sha256:stu_hash_109';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$xs0Jgo8UWgHFVe4DgKK1tA$1oa5XGZzF7URg3y7/DbIUC4g5MQddYnwN+nv2xnbc7A' WHERE `User_ID` = 110 AND `Password_hash` = 'sha256:stu_hash_110';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$MVRt43h2n06PIgC/lymTrg$EsUIdI4q0yMXD+N4YhjK0KKNiVN8vy5Y9y2DB++Dc5Y' WHERE `User_ID` = 111 AND `Password_hash` = 'sha256:stu_hash_111';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$J4Ibwpa1iKt+JAc2W3eBvg$vUEPPqNBDyUBtqTmtuj990Sq6n3UN5C+NZMNi/RoTWw' WHERE `User_ID` = 112 AND `Password_hash` = 'sha256:stu_hash_112';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$TQ7g2AmicbWDDOiAOGxHfw$qHo+TC2lpuZtrsu1hcqe/gFvT2YEYzKd/aDYuiR9cvw' WHERE `User_ID` = 113 AND `Password_hash` = 'sha256:stu_hash_113';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$lcPj1GKX8d4pVhB9ImbOmg$z/XZE2hzVwNS4ktZzUfbCeOpt8H8+xQFQWFr0238Ui0' WHERE `User_ID` = 114 AND `Password_hash` = 'sha256:stu_hash_114';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$NKKoTG5nvB3NN1mHxmewYQ$6DQcr3KLMctGTHPmZAJhHoEFX+zp5rDg9MiMSSHuc4I' WHERE `User_ID` = 115 AND `Password_hash` = 'sha256:stu_hash_115';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$R5RkwzUOLwVWCPLK9F/XVg$gFR8OUhjyF8WZ8QXtyWVjs+HdUuQvSq+yhPErebMskQ' WHERE `User_ID` = 116 AND `Password_hash` = 'sha256:stu_hash_116';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$iEwc4mSF1Sb0P7NJ0VcwrQ$VwzsGww1wABe6XF2D74qcKFaAm4INHeMomPmxOC5JlU' WHERE `User_ID` = 117 AND `Password_hash` = 'sha256:stu_hash_117';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$qyepnr1Vo4YboZUiNRuFHg$oYZrtZm0zXYcHfL6lvBTDvlHG7UjHey3LCERHRBC9ho' WHERE `User_ID` = 118 AND `Password_hash` = 'sha256:stu_hash_118';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$buPQVGGZatb7aQJttwBZEw$5V3Tq+/ak7JT/+9SUEUK87kAYddyWXmn4TLYGae5hEo' WHERE `User_ID` = 119 AND `Password_hash` = 'sha256:stu_hash_119';
UPDATE `USER` SET `Password_hash` = 'pbkdf2_sha256$120000$hjWAWk+hU7r9NG9bW08EUA$LM/djDEm8IEO9/jQUriUPNZd0WfFvTsUsVqxfhHubK4' WHERE `User_ID` = 120 AND `Password_hash` = 'sha256:stu_hash_120';
COMMIT;
SELECT `User_ID`, `Email`, LEFT(`Password_hash`, 20) AS hash_prefix FROM `USER` ORDER BY `User_ID`;
