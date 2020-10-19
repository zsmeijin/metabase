#!/usr/bin/env bash

# Determines whether we should skip tests for a driver, usage:
#
#    ./.circleci/skip-driver-tests.sh oracle

set -euo pipefail

driver="$1"

commit_message=`cat commit.txt`

parent_branch=`git log --decorate --simplify-by-decoration --oneline | grep -v "HEAD" | head -n 1 | sed -r 's/^\w+\s\(origin\/([^,)]+).+$/\1/'`
files_with_changes=`git diff --name-only "$parent_branch" | grep '.clj'`

if [[ "$CIRCLE_BRANCH" =~ ^master|release-.+$ ]]; then
    is_master_or_release_branch=true;
else
    is_master_or_release_branch=false;
fi

if [[ "$COMMIT_MESSAGE" == *"[ci all]"* ]]; then
    has_ci_drivers_commit_message=true;
elif [[ "$COMMIT_MESSAGE" == *"[ci drivers]"* ]]; then
    has_ci_drivers_commit_message=true;
elif [[ "$COMMIT_MESSAGE" == *"[ci $driver]"* ]]; then
    has_ci_drivers_commit_message=true;
else
    has_ci_drivers_commit_message=false;
fi

if [[ "$COMMIT_MESSAGE" == *"[ci quick]"* ]]; then
    has_ci_quick_message=true;
else
    has_ci_quick_message=false;
fi

if [ -n "$files_with_changes" ]; then
    has_backend_changes=true;
else
    has_backend_changes=false;
fi

echo "Master or release branch?" is_master_or_release_branch
echo "Has [ci drivers] or [ci all] or [ci $driver]?" has_ci_drivers_commit_message
echo "Has [ci quick] message?" has_ci_quick_message
echo "Has backend changes?" has_backend_changes

# ALWAYS run driver tests for master or release branches.
if is_master_or_release_branch; then
    exit 1;
fi

# ALWAYS run driver tests if the commit includes [ci all], [ci drivers], or [ci <driver>]
if has_ci_drivers_commit_message; then
    exit 2;
fi

# If any backend files have changed, run driver tests *unless* the commit includes [ci quick]
if has_backend_changes; then
    if has_ci_quick_message; then
        exit 0;
    else
        exit 3;
    fi
fi

exit 0
