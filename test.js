const ansi = require("gulp-cli/lib/shared/ansi");
const cliOptions = require("gulp-cli/lib/shared/cli-options");
const exit = require("gulp-cli/lib/shared/exit");
const gulp = require("gulp");
const log = require("gulplog");
const logEvents = require("gulp-cli/lib/versioned/^4.0.0/log/events");
const logSyncTask = require("gulp-cli/lib/versioned/^4.0.0/log/sync-task");
const toConsole = require("gulp-cli/lib/shared/log/to-console");
const yargs = require("yargs");
const muteStdout = require("mute-stdout");

const usage  = "$0\n"
      + ansi.bold("Usage:")
      + " gulp "
      + ansi.blue("[options]")
      + " tasks";
const parser = yargs.usage(usage).options(cliOptions);
const opts   = parser.argv;

toConsole(log, opts);
logEvents(gulp);
logSyncTask(gulp);

muteStdout.unmute();
gulp.task("test", function test (done) {
  console.log("test");
  done();
});

process.nextTick(() => {
  gulp.parallel([ "test" ])(err => {
    if (err) {
      exit(1);
    }
  });
});
