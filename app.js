(function () {
  const html = document.documentElement;
  const themeBtn = document.getElementById("themeToggleBtn");
  const themeIcon = document.getElementById("themeIcon");

  if (themeBtn && themeIcon) {
    themeBtn.addEventListener("click", function () {
      const next = html.getAttribute("data-theme") === "light" ? "dark" : "light";
      html.setAttribute("data-theme", next);
      themeIcon.textContent = next === "light" ? "🌙" : "☀";
    });
  }

  const mockup = document.getElementById("phoneMockup");
  const surfaceButtons = document.querySelectorAll("#surfacePills .seg-btn");
  const screenButtons = document.querySelectorAll("#screenPills .seg-btn");
  const screens = document.querySelectorAll(".phone-screen");

  surfaceButtons.forEach(function (btn) {
    btn.addEventListener("click", function () {
      surfaceButtons.forEach(function (b) { b.classList.remove("active"); });
      btn.classList.add("active");
      if (mockup) mockup.setAttribute("data-surface", btn.getAttribute("data-surface") || "pixel");
    });
  });

  screenButtons.forEach(function (btn) {
    btn.addEventListener("click", function () {
      const target = btn.getAttribute("data-screen") || "home";
      screenButtons.forEach(function (b) { b.classList.remove("active"); });
      btn.classList.add("active");
      if (mockup) mockup.setAttribute("data-screen", target);
      screens.forEach(function (sc) {
        sc.classList.toggle("active", sc.classList.contains("screen-" + target));
      });
    });
  });

  // Interactive Widget Stack demo
  const stackCards = [
    { tag: "WIDGET STACK • 1/3", title: "Daily Focus • 2:30 PM Review", sub: "Tap stack to cycle Calendar / Battery / Weather" },
    { tag: "WIDGET STACK • 2/3", title: "Pixel Battery • 98% Charging", sub: "Watch 82% • Buds Pro 100%" },
    { tag: "WIDGET STACK • 3/3", title: "Live Weather • 26°C Sunny", sub: "Feels like 25°C • UV Index Low" }
  ];
  let stackIdx = 0;
  const widgetStack = document.getElementById("mockWidgetStack");
  const stackTag = document.getElementById("stackTag");
  const stackTitle = document.getElementById("stackTitle");
  const stackSub = document.getElementById("stackSub");
  const stackDots = document.querySelectorAll(".stack-dots .sdot");

  if (widgetStack) {
    widgetStack.addEventListener("click", function () {
      stackIdx = (stackIdx + 1) % stackCards.length;
      const item = stackCards[stackIdx];
      if (stackTag) stackTag.textContent = item.tag;
      if (stackTitle) stackTitle.textContent = item.title;
      if (stackSub) stackSub.textContent = item.sub;
      stackDots.forEach(function (d, i) {
        d.classList.toggle("active", i === stackIdx);
      });
    });
  }

  // Interactive Play/Pause toggle inside At a Glance media demo
  const playBtn = document.getElementById("mockPlayBtn");
  if (playBtn) {
    let playing = true;
    playBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      playing = !playing;
      playBtn.textContent = playing ? "❚❚" : "▶";
    });
  }
})();
