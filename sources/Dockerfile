FROM sbtscala/scala-sbt:eclipse-temurin-8u352-b08_1.8.2_2.12.17

RUN \
  apt-get update && \
  apt-get install curl build-essential libssl-dev pkg-config git -y && \
  apt-get install z3 -y --no-install-recommends && \
  rm -rf /var/lib/apt/lists/*

USER sbtuser
ENV PATH=/home/sbtuser/.cargo/bin:$PATH

# Install rust
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal --default-toolchain none

# Add project
COPY --chown=sbtuser russol-alpha /home/sbtuser/russol-alpha
WORKDIR /home/sbtuser/russol-alpha

# Build russol
# "net.git-fetch-with-cli" required for building cross platform images
RUN cargo --config net.git-fetch-with-cli=true fetch
RUN cargo build --release
RUN cargo test --release --no-run

# Build suslik
ENV SUSLIK_DIR=/home/sbtuser/russol-alpha/suslik
RUN cargo run --release --bin ruslic ruslic/tests/synth/paper/rust/c-custom/general/clone.rs

# Prefetch crates for demo
COPY --chown=sbtuser demo /home/sbtuser/demo
RUN cargo fetch --manifest-path=/home/sbtuser/demo/Cargo.toml

# Prefetch crates for top_crates benchmark
RUN cargo test --release --test top_crates -- prefetch_cached

# Print outputs only relevant to evaluation
ENV RUSLIC_EVAL=true

ENTRYPOINT ["cargo"]
