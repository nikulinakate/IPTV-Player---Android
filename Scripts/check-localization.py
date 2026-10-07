#!/usr/bin/env python3
"""Check complete app translations before Android packaging; no external dependencies."""
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LOCALES = {
    "en": "values", "ru": "values-ru", "es": "values-es", "de": "values-de",
    "fr": "values-fr", "pt-BR": "values-pt-rBR", "it": "values-it", "ja": "values-ja",
    "ko": "values-ko", "zh-Hans": "values-b+zh+Hans", "tr": "values-tr", "ar": "values-ar",
}
PLURAL_FORMS = {
    "en": {"one", "other"}, "ru": {"one", "few", "many", "other"},
    "es": {"one", "many", "other"}, "de": {"one", "other"},
    "fr": {"one", "many", "other"}, "pt-BR": {"one", "many", "other"},
    "it": {"one", "many", "other"}, "ja": {"other"}, "ko": {"other"},
    "zh-Hans": {"other"}, "tr": {"one", "other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
}
FORMAT = re.compile(r"%(?:\d+\$)?[ds]")


def resources(path):
    root = ET.parse(path).getroot()
    keys = [(node.tag, node.attrib["name"]) for node in root]
    assert len(keys) == len(set(keys)), f"Duplicate resources in {path}"
    return {key: node for key, node in zip(keys, root)}


def signature(text):
    tokens = FORMAT.findall(text)
    assert all(re.fullmatch(r"%\d+\$[ds]", token) for token in tokens), f"Unnumbered placeholder: {text}"
    return Counter(tokens)


def main():
    base = resources(RES / "values/strings.xml")
    for locale, folder in LOCALES.items():
        localized = resources(RES / folder / "strings.xml")
        assert localized.keys() == base.keys(), f"{locale}: missing or extra keys: {base.keys() ^ localized.keys()}"
        for key, node in localized.items():
            if node.tag == "string":
                assert node.text and node.text.strip(), f"{locale}: empty {key}"
                assert signature(node.text) == signature(base[key].text), f"{locale}: placeholders differ in {key}"
            else:
                forms = {item.attrib["quantity"]: item for item in node}
                assert len(forms) == len(node), f"{locale}: duplicate plural forms in {key}"
                assert forms.keys() >= PLURAL_FORMS[locale], f"{locale}: missing plural forms in {key}"
                expected = signature(base[key].find("item[@quantity='other']").text)
                for quantity, item in forms.items():
                    assert item.text and item.text.strip(), f"{locale}: empty plural {key}/{quantity}"
                    # Arabic expresses zero/one/two in words; no numeric substitution is needed.
                    actual = signature(item.text)
                    assert actual == expected or (locale == "ar" and quantity in {"zero", "one", "two"} and not actual), f"{locale}: plural placeholders differ in {key}/{quantity}"
    config = ET.parse(RES / "xml/locale_config.xml").getroot()
    languages = [node.attrib["{http://schemas.android.com/apk/res/android}name"] for node in config]
    assert len(languages) == len(set(languages)) and set(languages) == set(LOCALES), "Language settings do not match translations"
    referenced = set()
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        referenced.update(re.findall(r"R\.(string|plurals)\.(\w+)", path.read_text()))
    assert referenced <= base.keys(), f"Unknown resources referenced: {referenced - base.keys()}"
    print(f"PASS: {len(LOCALES)} locales, {sum(k[0] == 'string' for k in base)} strings and {sum(k[0] == 'plurals' for k in base)} plurals each; placeholders and language settings match")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, ET.ParseError) as error:
        sys.exit(str(error))
