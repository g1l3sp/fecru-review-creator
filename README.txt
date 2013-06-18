  INTRODUCTION

This plugin facilitates automatic review generation for commits with files
whose paths match one of a set of expressions.  Review generation can be
enable/disabled on a per-project or path prefix basis.

Please note that the license has been inherited from the Atlassian-provided
fecru-review-creator project which was used as the basis for development.


  REQUIREMENTS

- FishEye _with_ Crucible (will not work with Crucible standalone)
- Release 2.1.0 or higher
- When running from the Plugin SDK, provide sufficient heap space:
  $ export ATLAS_OPTS=-Xmx512m
  $ atlas-run
