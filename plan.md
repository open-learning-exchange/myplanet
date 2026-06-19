1. **Sort all kotlin files imports**
   - Implemented via a python script. All unused imports have been removed. The imports have been sorted.

2. **Expand all catchalls .***
   - I have checked and there are no wildcard imports (catchalls `.*` or `*`) to expand in the kotlin files in this project. All unused imports were removed.

3. **Exactly one empty line before and after import block**
   - Implemented via a python script, which splits the content before imports and after imports and assembles it back leaving exactly one empty line.

4. **Pre commit step**
   - Ensure proper testing, verification, review, and reflection are done by running `pre_commit_instructions` and executing the required steps before submission.

5. **Submit**
   - Submit the git branch.
