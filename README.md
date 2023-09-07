[![Open Issues](https://img.shields.io/github/issues/pmonks/clojars-poms.svg)](https://github.com/pmonks/clojars-poms/issues)
![GitHub last commit](https://img.shields.io/github/last-commit/pmonks/clojars-poms.svg)
[![License](https://img.shields.io/github/license/pmonks/clojars-poms.svg)](https://github.com/pmonks/clojars-poms/blob/master/LICENSE)
<!-- [![Dependencies Status](https://versions.deps.co/pmonks/clojars-poms/status.svg)](https://versions.deps.co/pmonks/clojars-poms) -->

# clojars-poms

A little tool that started out as a way to explore the dependencies between projects deployed to Clojars, but is now a more generally useful tool for analysing the POMs of projects deployed to Clojars.

## Installation

For now the code isn't deployed anywhere, so best to clone this repo, then take a look at the source.

If you've installed the [Clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) you can run the sample script and be dropped in a REPL via:

```shell
$ clojure -i repl-init.clj -r
```

Note: the `repl-init.clj` script uses the [spinner](https://github.com/pmonks/spinner) library, which isn't compatible with the `clj` command line script.

The first time this script is run (and assuming the `prevent-sync` flag is set to `false`) it will pull down all POMs from clojars.org and cache them locally, which can take an hour or more depending on your network connection.  As of mid 2023, this is ~265,000 POM files (and the same number of metadata files for caching purposes) totalling ~2.4GB.  On subsequent runs it will be a lot faster (especially if `prevent-sync` is set to `true`!), as it uses etag requests to Clojars to only pull what's new or modified.

Look at [`repl-init.clj`](https://github.com/pmonks/clojars-poms/blob/master/repl-init.clj) for more details on what the script sets up and how you can experiment with this data.

## Developer Information

[GitHub project](https://github.com/pmonks/clojars-poms)

[Bug Tracker](https://github.com/pmonks/clojars-poms/issues)

## License

Copyright © 2019 Peter Monks (pmonks@gmail.com)

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
