import argparse
import re


def __replace_list(match, ordered=False):
    """
    Helper method for converting lists, both ordered and unordered

    :param match:   the regex match
    :param ordered: if the list is ordered
    :return:        the replaced bbcode for this list
    """
    items = match.group(1).strip().split('\n')

    idx = 3 if ordered else 2
    bbcode_items = ''.join([f'[*]{item[idx:]}\n' for item in items])

    suf = '=1' if ordered else ''
    return f'[LIST{suf}]\n{bbcode_items}[/LIST]'


def __convert_lists(md_text):
    """
    This regex insanity will transform lists (ordered and unordered).

    :param md_text: the Markdown text to convert
    :return:        the partially converted text (only lists are converted here)
    """

    # Unordered List
    md_text = re.sub(r'((?:^-\s.+\n?)+)', lambda x: __replace_list(x), md_text, flags=re.MULTILINE)

    # Ordered List
    md_text = re.sub(r'((?:^\d+\.\s.+\n?)+)', lambda x: __replace_list(x, ordered=True), md_text, flags=re.MULTILINE)

    return md_text


def convert(md_text):
    """
    This is a very naive converter from Markdown to BBCode.
    Used to generate BBCode to use in Spigot's home page.

    :param md_text: the Markdown text string
    :return:        the BBCode text string
    """
    # Headers
    md_text = re.sub(r'^# (.+)$', r'[B][SIZE=7]\1[/SIZE][/B]', md_text, flags=re.MULTILINE)
    md_text = re.sub(r'^## (.+)$', r'[B][SIZE=6]\1[/SIZE][/B]', md_text, flags=re.MULTILINE)
    md_text = re.sub(r'^### (.+)$', r'[B][SIZE=5]\1[/SIZE][/B]', md_text, flags=re.MULTILINE)
    md_text = re.sub(r'^#### (.+)$', r'[B][SIZE=4][COLOR=#808080]\1[/COLOR][/SIZE][/B]', md_text, flags=re.MULTILINE)

    # Bold, italic
    md_text = re.sub(r'\*\*([^*]+?)\*\*', r'[B]\1[/B]', md_text)
    md_text = re.sub(r'_(.+?)_', r'[I]\1[/I]', md_text)
    md_text = re.sub(r'\*(.+?)\*', r'[I]\1[/I]', md_text)

    # Links
    md_text = re.sub(r'\[(.+?)]\((http.+?)\)', r"[URL='\2']\1[/URL]", md_text)

    # Code
    md_text = re.sub(r'```([\S\s]+?)```', r'[FONT=Courier New][COLOR=#808080]\1[/COLOR][/FONT]', md_text,
                     flags=re.DOTALL)
    md_text = re.sub(r'`(.+?)`', r'[FONT=Courier New][COLOR=#808080]\1[/COLOR][/FONT]', md_text)

    md_text = __convert_lists(md_text)

    return md_text


if __name__ == "__main__":
    parser = argparse.ArgumentParser("markdown_to_bbcode")

    parser.add_argument("file", type=str, help="The name of the file to convert")
    parser.add_argument("--out", type=str, help="The file to write the bbcode to")

    args = parser.parse_args()

    if not args.file.endswith(".md"):
        print("Extension must be .md")
        exit(1)

    file = open(args.file)
    bbcode = convert(file.read())
    file.close()

    out_name = args.out if args.out else args.file.replace(".md", ".bb")
    out = open(out_name, "w")
    out.write(bbcode)
    out.close()
