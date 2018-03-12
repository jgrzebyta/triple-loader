#!/bin/bash
# This script was taken from
# https://plexus.github.io/autodoc/


#!/bin/bash

# Command that generates the HTML.
# Release version
export AUTODOC_CMD="boot version aot codox target"

# SNAPHOT version
# export AUTODOC_CMD="boot develop aot codox target"

# The directory where the result of $AUTODOC_CMD, the generated HTML, ends up. This
# is what gets committed to $AUTODOC_BRANCH.
export AUTODOC_DIR="target/gh-pages"

# The git remote to fetch from and push to.
# export AUTODOC_REMOTE="origin"

# Branch name to commit and push to
# export AUTODOC_BRANCH="gh-pages"

\curl -sSL https://raw.githubusercontent.com/plexus/autodoc/master/autodoc.sh | bash
