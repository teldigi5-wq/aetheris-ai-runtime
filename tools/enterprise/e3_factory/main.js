/* E3.0: genuine bidirectional scroll interaction, with reduced-motion fallback.
   No external scripts, telemetry, cookies, model calls, WebGL or production actions. */
(() => {
  "use strict";
  const section = document.querySelector("#experience");
  const device = document.querySelector("#device");
  const lid = document.querySelector("#device-lid");
  const stage = document.querySelector("#stage");
  const meter = document.querySelector("#scroll-progress");
  const fill = document.querySelector("#meter-fill");
  const status = document.querySelector("#scroll-status");
  const caption = document.querySelector("#stage-caption");
  if (!section || !device || !lid || !stage || !meter || !fill || !status || !caption) {
    return;
  }

  const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let requested = false;

  function progress() {
    const scrollDistance = Math.max(1, section.offsetHeight - window.innerHeight);
    const raw = -section.getBoundingClientRect().top / scrollDistance;
    return Math.min(1, Math.max(0, Number.isFinite(raw) ? raw : 0));
  }
  function render() {
    requested = false;
    const p = progress();
    const percentage = Math.round(p * 100);
    const reduced = motion.matches;
    stage.dataset.motion = reduced ? "reduced" : "enabled";
    stage.dataset.progress = String(percentage);
    meter.setAttribute("aria-valuenow", String(percentage));
    fill.style.transform = "scaleX(" + p.toFixed(5) + ")";
    if (reduced) {
      device.style.transform = "";
      lid.style.transform = "";
      status.textContent = "STATIC VIEW / " + String(percentage).padStart(2, "0") + "%";
      caption.textContent = "REDUCED MOTION / STATIC FORM";
      return;
    }
    // Actual 3D CSS transform, driven by scroll progress in both directions.
    const angleY = -28 + 57 * p;
    const angleX = 15 + 7 * p;
    const shiftY = -28 * p;
    const scale = 0.92 + 0.13 * p;
    device.style.transform =
      "translate3d(0," + shiftY.toFixed(2) + "px,0) rotateX(" +
      angleX.toFixed(2) + "deg) rotateY(" + angleY.toFixed(2) +
      "deg) scale(" + scale.toFixed(3) + ")";
    lid.style.transform = "rotateX(" + (-14 - 10 * p).toFixed(2) + "deg)";
    status.textContent = (p < 0.33 ? "ARRIVAL" : p < 0.69 ? "REVEAL" : "COMPLETE") +
      " / " + String(percentage).padStart(2, "0") + "%";
    caption.textContent = (p < 0.33 ? "ORIENTATION / INITIAL" :
      p < 0.69 ? "ORIENTATION / ROTATING" : "ORIENTATION / REVEALED");
  }
  function schedule() {
    if (requested) return;
    requested = true;
    window.requestAnimationFrame(render);
  }

  window.addEventListener("scroll", schedule, { passive: true });
  window.addEventListener("resize", schedule, { passive: true });
  if (typeof motion.addEventListener === "function") {
    motion.addEventListener("change", schedule);
  } else if (typeof motion.addListener === "function") {
    motion.addListener(schedule);
  }
  schedule();
})();
