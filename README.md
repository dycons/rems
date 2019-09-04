[![CircleCI](https://circleci.com/gh/CSCfi/rems.svg?style=svg)](https://circleci.com/gh/CSCfi/rems)

# Resource Entitlement Management System

## Getting started

### Development database

Run the official postgres docker image and initialize the database by running

```
./dev_db.sh
```

Which does roughly the following:

1. run a postgres container named `rems_test`
2. initialize the database with `resources/sql/init.sql`
3. create the schema and populates it with test data

When done you can stop (and automatically remove) the database.

```
docker stop rems_test
```

### Populating the database

- You can get some test data with `lein run test-data`

### Running the application

REMS is a Clojure+Clojurescript Single Page App.

To start the (clojure) backend:

```
lein run
```

To start the (clojurescript) frontend, run in another terminal:

```
lein figwheel
```

Point your browser to <http://localhost:3000>

### Running tests

To run unit tests:

```
lein kaocha unit
```

To run tests that need a database:

```
lein kaocha integration
```

To run build the JS bundle and run browser tests (requires chromedriver in $PATH, the alias also builds cljs):

```
lein browsertests
```

If browser tests fail, screenshots and DOM are written in `browsertest-errors`.

#### Clojurescript tests

First make sure you have the npm depenencies with

```
lein deps
```

and then just run

```
lein doo once
```

to run tests in Headless Chrome via Karma. 

You may need to run `npm install karma karma-cljs-test karma-chrome-launcher` first to install the necessary tools and packages.

#### Running all the tests

To conveniently run all the tests you can run the lein alias

```
lein alltests
```

## Component Guide

You can access the component guide at `/#/guide`. It contains all the
components in various configurations.

## Contributing

REMS is an open source project. In case you would like to contribute to its development, please refer to the [CONTRIBUTING.md](CONTRIBUTING.md) document.

## More documentation

Documentation files can be found under the [docs](./docs) folder.

Documentation can also be read from the browser by launching user docs server with the command:
`mkdocs serve`
or simply by visiting https://rems2docs.rahtiapp.fi.

Alternatively docker images can be used for running the documentation server:

```
docker build . -f docs-server/Dockerfile -t rems-mkdocs-server
docker run -it -p 8000:8000 --name rems-user-guide rems-mkdocs-server --rm
```

_Note_ live reload is disabled for the docker version of mkdocs.
