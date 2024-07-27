import argparse
import os
import re
import yaml
from collections.abc import MutableMapping


def is_language_file(file_path):
    return (os.path.isfile(file_path) and
            re.match(r"^\w{2}_\w{2}\.(?:yml|yaml)$", os.path.basename(file_path), re.IGNORECASE))


def get_language_files(path):
    return [f for f in os.listdir(path) if
            is_language_file(os.path.join(path, f))]


# https://stackoverflow.com/questions/6027558/flatten-nested-dictionaries-compressing-keys
def flatten(dictionary, parent_key='', separator='.'):
    items = []
    for key, value in dictionary.items():
        new_key = parent_key + separator + key if parent_key else key
        if isinstance(value, MutableMapping):
            items.extend(flatten(value, new_key, separator=separator).items())
        else:
            items.append((new_key, value))
    return dict(items)


if __name__ == "__main__":
    parser = argparse.ArgumentParser("validate.py")

    parser.add_argument("main_language", type=str, help="The main language filename to compare to all others")
    parser.add_argument("language_path", type=str, help="The path of all language files")

    args = parser.parse_args()

    main_language_file = None
    to_validate = []
    for lang_file in get_language_files(args.language_path):
        if os.path.basename(lang_file) == args.main_language:
            main_language_file = lang_file
            continue

        to_validate.append(lang_file)

    if main_language_file is None:
        print("Couldn't find main language file")
        exit(1)

    if len(to_validate) == 0:
        print("Nothing to validate")
        exit(0)

    print(f"Validating against {main_language_file}: {to_validate}")

    with open(os.path.join(args.language_path, main_language_file)) as f:
        all_keys = flatten(yaml.safe_load(f))

    errors = []
    for lang_file in to_validate:
        file_errors = []
        with open(os.path.join(args.language_path, lang_file)) as f:
            file_keys = flatten(yaml.safe_load(f))

        for key in all_keys:
            if key not in file_keys:
                file_errors.append(f"Key {key} is missing.")

        for key in file_keys:
            if key not in all_keys:
                file_errors.append(f"Extra key {key} not in main file")

        if len(file_errors) > 0:
            errors.append({
                "name": lang_file,
                "errors": file_errors,
            })

    if len(errors) == 0:
        print("All files validated successfully.")
        exit(0)

    print("Some files failed to validate:")
    for file in errors:
        print(f"\tFile {file['name']}:")
        for error in file["errors"]:
            print(f"\t\t- {error}")

    exit(1)
