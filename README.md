[![Open Issues](https://img.shields.io/github/issues/pmonks/clojars-dependencies.svg)](https://github.com/pmonks/clojars-dependencies/issues)
![GitHub last commit](https://img.shields.io/github/last-commit/pmonks/clojars-dependencies.svg)
[![License](https://img.shields.io/github/license/pmonks/clojars-dependencies.svg)](https://github.com/pmonks/clojars-dependencies/blob/master/LICENSE)
<!-- [![Dependencies Status](https://versions.deps.co/pmonks/clojars-dependencies/status.svg)](https://versions.deps.co/pmonks/clojars-dependencies) -->

# clojars-dependencies

A little tool for exploring the dependencies between projects deployed to Clojars.

## Installation

For now the code isn't deployed anywhere, so best to clone this repo, then take a look at the source.

If you've installed the [Clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) you can run the sample script and be dropped in a REPL via:

```shell
$ clojure -i repl-init.clj -r
```

Note: the `repl-init.clj` script uses the [spinner](https://github.com/pmonks/spinner) library, which isn't compatible with the `clj` command line script.

The first time this script is run it will pull down all POMs from clojars.org, which can take an hour or more depending on your network connection.  As of mid 2022, this is ~250,000 POM files (and the same amount of metadata files for caching purposes) totalling ~2.2GB.

Look at [`repl-init.clj`](https://github.com/pmonks/clojars-dependencies/blob/master/repl-init.clj) for more details on what the script sets up and how you can experiment with this data.

## Developer Information

[GitHub project](https://github.com/pmonks/clojars-dependencies)

[Bug Tracker](https://github.com/pmonks/clojars-dependencies/issues)

## License

Copyright © 2019 Peter Monks (pmonks@gmail.com)

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
