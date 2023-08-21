FROM ubuntu:jammy

RUN \
  apt-get update && \
  apt-get install curl build-essential libssl-dev pkg-config git -y && \
  apt-get install z3 -y --no-install-recommends && \
  rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash cuser
WORKDIR /home/cuser
ENV PATH=/home/cuser/.opam/default/bin:$PATH

# Install why3, but reduce layer size by not including opam
RUN \
  apt-get update && \
  apt-get install opam autoconf -y && \
  su -c "opam init --disable-sandboxing" cuser && \
  su -c "opam pin add --yes why3 https://gitlab.inria.fr/why3/why3.git#8641e4ae51c26e3aa1ee381afd8e9551e9483790" cuser && \
  su -c "opam install --yes ocamlgraph why3" cuser && \
  apt-get autoremove opam autoconf -y && \
  rm -rf /var/lib/apt/lists/*

USER cuser

RUN why3 config detect

# Install rust
ENV PATH=/home/cuser/.cargo/bin:$PATH
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal --default-toolchain none

# Download creusot
RUN git clone https://github.com/xldenis/creusot.git /home/cuser/creusot
WORKDIR /home/cuser/creusot

# Build creusot
ENV CARGO_INCREMENTAL=false
RUN cargo fetch
RUN cargo build
RUN cargo test

COPY prove prove

ENTRYPOINT ["bash"]
