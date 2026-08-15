//! Pure tao. No wry, no webview, no custom protocol, no IPC.
//!
//! One question only: is a user event sent through `EventLoopProxy::send_event`
//! *before* `event_loop.run()` delivered when the loop starts?
//!
//! Event 1 is sent before `run()`. Event 2 is sent by a background thread five
//! seconds later, long after the loop is running.
//!
//! Correct behaviour: event 1 arrives as the loop starts, event 2 at ~5 s.
//! Observed behaviour is in the log this prints.

use std::{
  sync::OnceLock,
  time::{Duration, Instant},
};

use tao::{
  event::Event,
  event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy},
};

static T0: OnceLock<Instant> = OnceLock::new();
static PROXY: OnceLock<EventLoopProxy<u32>> = OnceLock::new();

fn ms() -> u128 {
  T0.get_or_init(Instant::now).elapsed().as_millis()
}

/// tao redirects stdout/stderr to logcat under the tag `RustStdoutStderr`.
fn log(msg: &str) {
  println!("[taoraw + {:5} ms] {}", ms(), msg);
}

pub fn run_app() {
  T0.get_or_init(Instant::now);
  log("run_app() entered");

  let event_loop = EventLoopBuilder::<u32>::with_user_event().build();
  let proxy = event_loop.create_proxy();
  let _ = PROXY.set(proxy.clone());

  // Sent while the loop does not yet exist to receive it.
  log("sending user event 1 BEFORE run()");
  if proxy.send_event(1).is_err() {
    log("send of event 1 failed");
  }

  // Sent long after the loop is running, to see whether it flushes event 1.
  std::thread::spawn(|| {
    std::thread::sleep(Duration::from_secs(5));
    log("sending user event 2 (loop has been running for ~5 s)");
    if let Some(proxy) = PROXY.get() {
      if proxy.send_event(2).is_err() {
        log("send of event 2 failed");
      }
    }
  });

  // A heartbeat, so the log shows the loop is alive and waiting rather than stuck.
  std::thread::spawn(|| loop {
    std::thread::sleep(Duration::from_secs(1));
    log("heartbeat (loop thread is not blocked)");
  });

  // Match the wry repro: startup work delays run() by 3 s.
  log("busy for 3000 ms before run(), like an app doing startup work");
  std::thread::sleep(Duration::from_millis(3000));

  log("calling event_loop.run() now");
  event_loop.run(move |event, _, control_flow| {
    *control_flow = ControlFlow::Wait;

    if let Event::UserEvent(n) = event {
      log(&format!(">>> USER EVENT {n} RECEIVED"));
    }
  });
}

fn stop_unwind<F: FnOnce() -> T, T>(f: F) -> T {
  match std::panic::catch_unwind(std::panic::AssertUnwindSafe(f)) {
    Ok(t) => t,
    Err(err) => {
      eprintln!("attempt to unwind out of `rust` with err: {err:?}");
      std::process::abort()
    }
  }
}

/// tao's `android_binding!` wants an on-activity-create hook with this exact
/// signature. wry supplies one; without wry there is nothing to do here.
#[cfg(target_os = "android")]
unsafe fn noop_setup(
  _package: &str,
  _env: jni::JNIEnv,
  _looper: &ndk::looper::ThreadLooper,
  _activity: jni::objects::GlobalRef,
) {
}

#[cfg(target_os = "android")]
fn _start_app() {
  use tao::platform::android::prelude::*;

  tao::android_binding!(org_repro, taoraw, Rust, noop_setup, _start_app, ::tao);

  stop_unwind(run_app);
}
