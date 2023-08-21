# Prerequisite
* Install `russol-amd64.tar.gz` from the [artifact](https://zenodo.org/record/7811786) on the base directory
  * `wget https://zenodo.org/record/7811786/files/russol-amd64.tar.gz?download=1`

# Explanation
* `demo.rs`: programs to synthesize 
* `russol-amd64.tar.gz`: a docker tarball with the synthesizer binary
* `sources/`: the source code (unnecessary for execution, but can check tests and source codes)

# How to run
1. 
```bash
docker load -i ../russol-arch.tar.gz
```

2.
```bash
docker run --rm -it -v ${PWD}/demo.rs:/demo.rs jonasalaif/russol run --release --bin ruslic /demo.rs
```
