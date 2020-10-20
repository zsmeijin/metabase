#!/usr/bin/env bash

# Determines whether we should skip tests for a driver, usage:
#
#    ./.circleci/skip-driver-tests.sh oracle

set -euo pipefail

driver="$1"

if [[ "$CIRCLE_BRANCH" =~ ^master|release-.+$ ]]; then
    is_master_or_release_branch=true;
else
    is_master_or_release_branch=false;
fi

commit_message=`cat commit.txt`

if [[ "$commit_message" == *"[ci all]"* ]]; then
    has_ci_drivers_commit_message=true;
elif [[ "$commit_message" == *"[ci drivers]"* ]]; then
    has_ci_drivers_commit_message=true;
elif [[ "$commit_message" == *"[ci $driver]"* ]]; then
    has_ci_drivers_commit_message=true;
else
    has_ci_drivers_commit_message=false;
fi

if [[ "$commit_message" == *"[ci quick]"* ]]; then
    has_ci_quick_message=true;
else
    has_ci_quick_message=false;
fi

backend_files_with_changes=`cat files_with_changes.txt | grep '*.clj'`

if [ -n "$backend_files_with_changes" ]; then
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
    echo "Running driver tests because this is a build on master or a release branch"
    exit 1;
fi

# ALWAYS run driver tests if the commit includes [ci all], [ci drivers], or [ci <driver>]
if has_ci_drivers_commit_message; then
    echo "Running driver tests because commit message includes [ci all], [ci drivers], or [ci $driver]"
    exit 2;
fi

# If any backend files have changed, run driver tests *unless* the commit includes [ci quick]
if has_backend_changes; then
    if has_ci_quick_message; then
        echo "Skipping driver tests because commit message includes [ci quick]"
        exit 0;
    else
        echo "Running driver tests because there are changes to backend files in this branch"
        exit 3;
    fi
fi

echo "Skipping driver tests because branch includes no backend changes"
exit 0
