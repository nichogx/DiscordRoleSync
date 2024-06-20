from unittest import TestCase

import markdown_to_bbcode


class ConverterTestCase:
    def __init__(self, markdown, expect_bb):
        self.markdown = markdown
        self.expect_bb = expect_bb


class TestConversion(TestCase):
    def assertConverts(self, markdown, expected):
        self.assertEqual(
            expected,
            markdown_to_bbcode.convert(markdown),
            "Incorrect bbcode returned."
        )

    def test_random_string(self):
        self.assertConverts("random string", "random string")

    def test_titles(self):
        self.assertConverts("# Title", "[B][SIZE=7]Title[/SIZE][/B]"),
        self.assertConverts("## Title2", "[B][SIZE=6]Title2[/SIZE][/B]"),
        self.assertConverts("### Title3", "[B][SIZE=5]Title3[/SIZE][/B]"),
        self.assertConverts("#### Title4", "[B][SIZE=4][COLOR=#808080]Title4[/COLOR][/SIZE][/B]"),

    def test_bold_italic(self):
        self.assertConverts("**some random bold text**", "[B]some random bold text[/B]")
        self.assertConverts("_some random italic text_", "[I]some random italic text[/I]")
        self.assertConverts("*some random italic text*", "[I]some random italic text[/I]")
        self.assertConverts("**_some random bold italic text_**", "[B][I]some random bold italic text[/I][/B]")
        self.assertConverts("_**some random bold italic text**_", "[I][B]some random bold italic text[/B][/I]")
        self.assertConverts("***some random bold italic text***", "[I][B]some random bold italic text[/B][/I]")

    def test_links(self):
        self.assertConverts("[link to something](https://bla)", "[URL='https://bla']link to something[/URL]")

    def test_unordered_lists(self):
        self.assertConverts(
            """
- item 1
- item 2
- item 3
            """.strip(),
            """
[LIST]
[*]item 1
[*]item 2
[*]item 3
[/LIST]
            """.strip()
        )

        self.assertConverts(
            """
- item 1
- item 2
- item 3

bla bla

- item 4
- item 5
- item 6
            """.strip(),
            """
[LIST]
[*]item 1
[*]item 2
[*]item 3
[/LIST]
bla bla

[LIST]
[*]item 4
[*]item 5
[*]item 6
[/LIST]
            """.strip()
        )

    def test_ordered_lists(self):
        self.assertConverts(
            """
1. item 1
2. item 2
3. item 3
            """.strip(),
            """
[LIST=1]
[*]item 1
[*]item 2
[*]item 3
[/LIST]
            """.strip()
        )

    def test_code(self):
        self.assertConverts("`some code`", "[FONT=Courier New][COLOR=#808080]some code[/COLOR][/FONT]")
        self.assertConverts("```\nsome code\nbla\n```",
                            "[FONT=Courier New][COLOR=#808080]\nsome code\nbla\n[/COLOR][/FONT]")
