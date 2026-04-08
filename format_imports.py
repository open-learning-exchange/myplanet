import os
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    lines = content.splitlines()

    import_lines_indices = []
    for i, line in enumerate(lines):
        if line.startswith("import "):
            import_lines_indices.append(i)

    if not import_lines_indices:
        return

    first_imp = import_lines_indices[0]
    last_imp = import_lines_indices[-1]

    imports = []
    for i in import_lines_indices:
        imp = lines[i].strip()
        if ".*" in imp:
            if "io.mockk" in imp:
                imports.extend([
                    "import io.mockk.MockKAnnotations",
                    "import io.mockk.Called",
                    "import io.mockk.every",
                    "import io.mockk.mockk",
                    "import io.mockk.spyk",
                    "import io.mockk.verify",
                    "import io.mockk.unmockkAll",
                    "import io.mockk.mockkObject",
                    "import io.mockk.mockkClass",
                    "import io.mockk.mockkStatic",
                    "import io.mockk.coEvery",
                    "import io.mockk.coVerify",
                    "import io.mockk.just",
                    "import io.mockk.runs",
                    "import io.mockk.slot",
                    "import io.mockk.impl.annotations.MockK",
                    "import io.mockk.Runs",
                ])
            elif "org.junit.Assert" in imp:
                imports.extend([
                    "import org.junit.Assert.assertEquals",
                    "import org.junit.Assert.assertNotNull",
                    "import org.junit.Assert.assertTrue",
                    "import org.junit.Assert.assertFalse",
                    "import org.junit.Assert.assertNull",
                    "import org.junit.Assert.assertArrayEquals",
                    "import org.junit.Assert.assertSame",
                    "import org.junit.Assert.assertNotSame",
                    "import org.junit.Assert.fail"
                ])
            else:
                imports.append(imp)
        else:
            imports.append(imp)

    imports = list(set(imports))

    non_import_lines = [l for i, l in enumerate(lines) if not l.startswith("import ")]
    code_text = "\n".join(non_import_lines)

    words_in_code = set(re.findall(r'[A-Za-z0-9_]+', code_text))
    # We should also handle property access like `.value`, or extension functions. But matching exact words is usually enough for imports since you usually refer to them.

    final_imports = []
    for imp in imports:
        match = re.search(r'import\s+([A-Za-z0-9_\.]+)(?:\s+as\s+([A-Za-z0-9_]+))?', imp)
        if match:
            full_path = match.group(1)
            alias = match.group(2)

            last_part = alias if alias else full_path.split('.')[-1]

            if last_part == '*' or last_part in words_in_code:
                # Mockito is a special case: `import org.mockito.Mockito.mock` is fine if `mock` is used. But wait, we saw `org.mockito.Mockito.when` is used with backticks.
                # Let's ensure 'when' doesn't get removed if backticks are used. 'when' will be matched by regex as [A-Za-z0-9_]+ because it strips backticks.
                # However, what if `when` is not used in the code text?
                final_imports.append(imp)
            elif imp == "import org.mockito.Mockito.`when`" and 'when' in words_in_code:
                 final_imports.append(imp)
        else:
            final_imports.append(imp)

    final_imports = sorted(list(set(final_imports)))

    lines_before = lines[:first_imp]
    while lines_before and lines_before[-1].strip() == '':
        lines_before.pop()

    code_after_imports = lines[last_imp+1:]
    while code_after_imports and code_after_imports[0].strip() == '':
        code_after_imports.pop(0)

    if final_imports:
        new_lines = lines_before + [''] + final_imports + [''] + code_after_imports
    else:
        new_lines = lines_before + [''] + code_after_imports

    new_content = "\n".join(new_lines) + "\n"

    with open(filepath, 'w') as f:
        f.write(new_content)

for root, dirs, files in os.walk('.'):
    if 'build' in dirs:
        dirs.remove('build')
    for f in files:
        if f.endswith('.kt'):
            process_file(os.path.join(root, f))
