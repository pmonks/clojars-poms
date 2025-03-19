[![Open Issues](https://img.shields.io/github/issues/pmonks/clojars-poms.svg)](https://github.com/pmonks/clojars-poms/issues)
![GitHub last commit](https://img.shields.io/github/last-commit/pmonks/clojars-poms.svg)
[![License](https://img.shields.io/github/license/pmonks/clojars-poms.svg)](https://github.com/pmonks/clojars-poms/blob/master/LICENSE)
<!-- [![Dependencies Status](https://versions.deps.co/pmonks/clojars-poms/status.svg)](https://versions.deps.co/pmonks/clojars-poms) -->

# clojars-poms

A little tool to support analysis of the POMs of projects deployed to Clojars.

## Installation

For now the code isn't deployed anywhere, so best to clone this repo, then take a look at the source.

If you've installed the [Clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) you can synchronise Clojars' POMs, parse the POM of the latest version of each project, then be dropped in a REPL, by running:

```shell
$ clojure -i repl-init.clj -r
```

Note: the `repl-init.clj` script uses the [spinner](https://github.com/pmonks/spinner) library, which isn't compatible with the `clj` command line script.  The incompatibilities are cosmetic only though, so if you'd rather have an ergonomic REPL experience, it's fine to use `clj` and just be ready for strange output while Clojars POMs are being synced.

The `repl-init.clj` script will print some helpful information about what it has populated and what functions are available for interrogating the parsed POMs, and you can look at [`repl-init.clj`](https://github.com/pmonks/clojars-poms/blob/master/repl-init.clj) for more details.

### IMPORTANT NOTE ABOUT DATA USAGE

**The first time this script is run it will pull down all POMs from clojars.org and cache them locally, which can take an hour or more depending on your network connection.**  As of spring 2025, this is ~295,000 POM files (and the same number of metadata files for caching purposes) totalling ~2.7GB.  On subsequent runs it will be a lot faster, both because you will be prompted whether to refresh the cache at all, and if you elect to do so, HTTP etag requests will be used to pull only what's new or modified.

**Please limit how often you re-sync poms from Clojars!**  They provide a wonderful service to the Clojure community for free, but someone is paying for their bandwidth and those folks deserve our respect!

## Developer Information

[GitHub project](https://github.com/pmonks/clojars-poms)

[Bug Tracker](https://github.com/pmonks/clojars-poms/issues)

## License

Copyright © 2019 Peter Monks (pmonks@gmail.com)

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
