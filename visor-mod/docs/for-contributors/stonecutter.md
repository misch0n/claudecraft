Stonecutter is a great tool to manage multi-version project in a single branch.

But, it requires different approach to developing such projects.

Visor contributors must follow these rules and tips, to not mess up the codebase:

# Rules
## General
1. Before contributing, run compile on all versions, to ensure you didn't break other versions

2. Make commits only from the latest version Visor supports.

If you switch to older version and commit, commented out and commented stonecutter
blocks will be seen as "changes", messing up commits history.

3. Minimize the amount of stonecutter code by using cross-version adapters and utils

Stonecutter code is harder to read and understand, therefore we need to minimize its usage,
hide in abstractions.
E.g. ResourceLocation was renamed in newer versions. Instead of applying the rename in hundreds classes,
make a cross-version adapter. 
These adapters provide stability of logic in all versions Visor supports.

For more info check api/compatibility/mcversion
## Mixins

# Tips

## General
1. Do not use "Optimize imports" feature in IDE

"Optimize imports" may remove stonecutter comments breaking multi-versioning