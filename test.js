const gulp = require("gulp");


gulp.task("test", function test (done) {
  console.log("test");
  done();
});

gulp.parallel(["test"]);