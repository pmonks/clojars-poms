[![Open Issues](https://img.shields.io/github/issues/pmonks/clojars-dependencies.svg)](https://github.com/pmonks/clojars-dependencies/issues)
[![License](https://img.shields.io/github/license/pmonks/clojars-dependencies.svg)](https://github.com/pmonks/clojars-dependencies/blob/master/LICENSE)
[![Dependencies Status](https://versions.deps.co/pmonks/clojars-dependencies/status.svg)](https://versions.deps.co/pmonks/clojars-dependencies)

# clojars-dependencies

A little tool for exploring the dependencies between projects deployed to Clojars.

## Installation

For now the script isn't deployed anywhere, so best to clone this repo, then take a look at the source.

If you've installed the [Clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) you can run the sample script and be dropped in a REPL via:

```shell
$ clj -i repl-init.clj -r
```

Note that the first time this script is run it will pull down approximately 950MB of POMs from clojars.org (using `rsync`), which can take 10 or more minutes depending on your network connection.

On every run, the script parses all of those XML files (approximately 180,000 of them, as of mid-2019), which also takes quite a bit of time.  Look at the source for more details on what the script sets up for you to experiment with.

## Developer Information

[GitHub project](https://github.com/pmonks/clojars-dependencies)

[Bug Tracker](https://github.com/pmonks/clojars-dependencies/issues)

## License

Copyright © 2019 Peter Monks (pmonks@gmail.com)

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
