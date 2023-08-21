# Structure of the artifact

The tool is located at `/home/sbtuser/russol-alpha` within the image (the working directory when running the image). A copy of this directory is included in `russol-alpha` at the root of the artifact, to allow files to be inspected locally. We now explain the structure of this directory.

- The `ruslic` directory contains the code for the frontend translation of the Rust signature and types into and SOL task.
  - The `ruslic/src/suslik*.rs` files handle translation to SOL, the others interface with the rustc to pull out the required signatures and annotations.
- The `ruslic/tests` directory contains both the test harnesses as well as the files tested on.
  - The `ruslic/tests/ci.rs` file is the test harness which runs synthesis on all Rust files under `ruslic/tests/synth`, of these we are interested in the `ruslic/tests/synth/paper` ones.
  - The `ruslic/tests/ci.rs` file is the test harness which runs synthesis on the top 100 crates, which are cached under `ruslic/tmp`.
  - The two `ruslic/tests/*-results.txt` files give the expected outputs of running the above two harnesses
  - The `ruslic/tests/all` directory contains all synthesized Rust functions in one file for easy viewing, it also contains the creusot version which was verified
- The `russol-contracts` and `russol-macros` directories define the procedural macros for annotating signatures (e.g. `#[requires(...)]`, `#[pure]` etc.)
- The `suslik` directory contains a heavily modified version of SuSLik, used as the backend for solving SOL tasks. The biggest changes were done in the following files:
  - `suslik/src/main/scala/org/tygus/suslik/synthesis/rules/*.scala` - modified for SOL rules
  - `suslik/src/main/scala/org/tygus/suslik/synthesis/tactics/RustSynthesis.scala` - application order of the rules
  - `suslik/src/main/scala/org/tygus/suslik/logic/*.scala` - internal representation of SOL heaplets
  - `suslik/src/main/scala/org/tygus/suslik/language/Statements.scala` - pretty printing to Rust syntax
