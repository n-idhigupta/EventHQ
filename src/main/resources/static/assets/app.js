(() => {
  "use strict";
  const $ = (s, root = document) => root.querySelector(s),
    main = $("#main"),
    modal = $("#modal"),
    modalContent = $("#modal-content");
  const esc = (value) =>
    String(value ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  const icon = (name) =>
    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">${{ search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>', pin: '<path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/>', calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M7 2v6M17 2v6"/>' }[name]}</svg>`;
  const read = (key) => {
    try {
      return JSON.parse(sessionStorage.getItem(key));
    } catch {
      return null;
    }
  };
  const store = (key, value) => {
    try {
      value === null
        ? sessionStorage.removeItem(key)
        : sessionStorage.setItem(key, JSON.stringify(value));
    } catch {}
  };
  const claims = (token) => {
    try {
      return JSON.parse(
        atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
      );
    } catch {
      return null;
    }
  };
  const state = {
    auth: read("eventhq.auth"),
    events: [],
    venues: [],
    sponsors: [],
    adminEvents: [],
    route: "discover",
    adminTab: "events",
    search: "",
    venue: "",
    sort: "date",
    selected: new Set(),
    currentEvent: null,
    inventory: [],
    hold: null,
    loaded: false,
    authMode: "login",
    afterAuth: null,
  };
  if (
    state.auth &&
    (!claims(state.auth.token) ||
      claims(state.auth.token).exp * 1000 <= Date.now())
  )
    state.auth = null;
  const isAdmin = () =>
    Boolean(state.auth && claims(state.auth.token)?.roles?.includes("ADMIN"));
  const uid = () => (state.auth ? claims(state.auth.token)?.sub : null),
    holdKey = () => `eventhq.hold.${uid()}`;
  function saveHold(hold) {
    state.hold = hold;
    if (uid()) store(holdKey(), hold);
  }
  function restoreHold() {
    state.hold = uid() ? read(holdKey()) : null;
    if (
      state.hold &&
      (state.hold.booking.status !== "HELD" ||
        new Date(state.hold.booking.expiresAt) <= new Date())
    )
      saveHold(null);
  }
  restoreHold();
  const money = (amount, currency = "INR") =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  const date = (value, options = { day: "numeric", month: "short" }) =>
    new Intl.DateTimeFormat("en-IN", options).format(new Date(value));
  const venueName = (id) =>
      state.venues.find((v) => v.id === id)?.name || `Venue ${id}`,
    eventName = (id) =>
      state.events.find((e) => e.id === id)?.name || `Event ${id}`;
  const remaining = (b) =>
    Math.max(0, Math.ceil((new Date(b.expiresAt) - Date.now()) / 1000));
  const timer = (b) => {
    const n = remaining(b);
    return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, "0")}`;
  };
  let toastTimer,
    routeVersion = 0;
  function toast(message) {
    $("#toast").textContent = message;
    $("#toast").hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => ($("#toast").hidden = true), 5500);
  }
  function updateAccount() {
    $("#account-button").textContent = state.auth
      ? state.auth.email.split("@")[0] + " ↗"
      : "Sign in ↗";
    $("#logout-button").hidden = !state.auth;
    $("#admin-nav").hidden = !isAdmin();
  }
  function clearAuth() {
    state.auth = null;
    state.hold = null;
    store("eventhq.auth", null);
    updateAccount();
  }
  async function api(path, { method = "GET", body, publicRetry = true } = {}) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (state.auth && !path.startsWith("/api/v1/auth/"))
      headers.Authorization = `Bearer ${state.auth.token}`;
    const controller = new AbortController(),
      timeout = setTimeout(() => controller.abort(), 20000);
    let response;
    try {
      response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (e) {
      throw new Error(
        e.name === "AbortError"
          ? "The request timed out. Please retry; payment retries are protected."
          : "Unable to reach EventHQ. Check that the server is running.",
      );
    } finally {
      clearTimeout(timeout);
    }
    let result = null;
    try {
      result = await response.json();
    } catch {
      if (response.ok && response.status !== 204)
        throw new Error("The server returned an unexpected response.");
    }
    if (!response.ok) {
      if (response.status === 401 && state.auth) {
        clearAuth();
        if (
          publicRetry &&
          method === "GET" &&
          ["/api/v1/events", "/api/v1/venues"].some((prefix) =>
            path.startsWith(prefix),
          )
        )
          return api(path, { publicRetry: false });
      }
      const details = result?.fieldErrors
        ? Object.entries(result.fieldErrors)
            .map(([key, value]) => `${key}: ${value}`)
            .join("; ")
        : "";
      throw new Error(
        details ||
          (response.status === 401
            ? "Please sign in to continue."
            : result?.message) ||
          `Request failed (${response.status}).`,
      );
    }
    return result;
  }
  async function paymentApi(id, body, key) {
    const controller = new AbortController(),
      timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(`/api/v1/bookings/${id}/payments`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${state.auth.token}`,
          "Idempotency-Key": key,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
      const data = await response.json();
      if (!response.ok) {
        if (response.status === 401) clearAuth();
        throw new Error(data.message || "Payment could not be completed.");
      }
      return data;
    } catch (e) {
      if (e.name === "AbortError")
        throw new Error(
          "Confirmation timed out. Retry to check the same payment safely.",
        );
      throw e;
    } finally {
      clearTimeout(timeout);
    }
  }
  async function allEvents(admin = false) {
    let page = 0,
      result,
      events = [];
    do {
      result = await api(
        `/api/v1/${admin ? "admin/" : ""}events?page=${page}&size=100`,
      );
      events.push(...result.content);
      page++;
    } while (page < result.totalPages);
    return events;
  }
  async function loadCatalog() {
    const [events, venues] = await Promise.all([
      allEvents(),
      api("/api/v1/venues"),
    ]);
    state.events = events;
    state.venues = venues;
    state.loaded = true;
  }
  function errorBlock(message) {
    return `<div class="error-block" role="alert">${esc(message)}<br><button class="secondary" data-action="retry">Try again ↻</button></div>`;
  }
  function empty(title, message, action = "", label = "") {
    return `<div class="empty-state"><div class="empty-symbol">✳</div><h3>${esc(title)}</h3><p>${esc(message)}</p>${action ? `<button class="primary" data-action="${action}">${esc(label)} <span>↗</span></button>` : ""}</div>`;
  }
  function holdBanner() {
    return state.hold
      ? `<div class="hold-banner"><span>Your seats are on hold · <strong data-timer>${timer(state.hold.booking)}</strong> remaining</span><button class="text-button" data-action="resume">Continue your booking ↗</button></div>`
      : "";
  }
  const posterThemes = [
    ["plum", "Nights to<br>remember.", "MAKE ROOM FOR THE MOMENT"],
    ["sage", "A fresh<br>perspective.", "IDEAS. PEOPLE. POSSIBILITY."],
    ["", "Meet you<br>there.", "SOMETHING TO LOOK FORWARD TO"],
    ["blue", "Outside<br>the everyday.", "YOUR NEXT GREAT STORY"],
    ["gold", "Make it<br>a memory.", "SHOW UP. FEEL SOMETHING."],
  ];
  function card(e) {
    const theme = posterThemes[(e.id - 1) % posterThemes.length],
      early = new Date(e.earlyBirdUntil) > new Date();
    return `<article class="event-card"><div class="poster ${theme[0]}"><div class="poster-top"><span>THE EVENTHQ EDIT</span><span>No. ${String(e.id).padStart(2, "0")}</span></div><div class="poster-title">${theme[1]}</div><div class="poster-art"></div><span class="poster-sticker">✳</span><span class="poster-sub">${theme[2]}</span></div><div class="card-body"><div class="card-category"><span>IN GOOD COMPANY</span>${early ? "<span>EARLY BIRD OPEN</span>" : "<span>LIVE EXPERIENCE</span>"}</div><h3>${esc(e.name)}</h3><div class="card-meta">${icon("calendar")}<span>${date(e.startsAt, { weekday: "short", day: "numeric", month: "short" })} · ${date(e.startsAt, { hour: "numeric", minute: "2-digit" })}</span></div><div class="card-meta">${icon("pin")}<span>${esc(venueName(e.venueId))}</span></div><div class="card-bottom"><div class="price"><small>Standard price</small><strong>${money(e.basePrice, e.currency)}</strong></div><button class="book-button" data-action="event" data-id="${e.id}">${new Date(e.startsAt) > new Date() ? "Find your seat ↗" : "View event ↗"}</button></div></div></article>`;
  }
  function renderCards() {
    const search = state.search.toLocaleLowerCase();
    let events = state.events.filter(
      (e) =>
        (!state.venue || String(e.venueId) === state.venue) &&
        `${e.name} ${venueName(e.venueId)}`
          .toLocaleLowerCase()
          .includes(search),
    );
    events.sort(
      state.sort === "price"
        ? (a, b) => a.basePrice - b.basePrice
        : (a, b) => new Date(a.startsAt) - new Date(b.startsAt),
    );
    $("#event-count").textContent = events.length;
    $("#event-grid").innerHTML = events.length
      ? events.map(card).join("")
      : empty(
          "A little quiet here.",
          "Try a different search or venue. More experiences are on their way.",
        );
  }
  function discover() {
    main.innerHTML = `<section class="hero"><div><div class="eyebrow">GO OUT. TUNE IN. BE THERE.</div><h1>Good plans start with<br>a <em>great event.</em></h1><p class="hero-description">For the music that moves you, the ideas that stay with you,<br>and the people you haven’t met yet. Find your next thing.</p></div><div class="hero-visual" aria-hidden="true"><div class="mini-ticket"><div class="ticket-copy"><small>YOU?RE INVITED TO</small><p>Something<br>extraordinary.</p><span>ONE MOMENT. ALL YOURS.</span></div><div class="ticket-end">ADMIT YOURSELF</div></div><span class="floating-star">✳</span><span class="hero-caption">a little anticipation looks good on you.</span></div></section>${holdBanner()}<section aria-label="Find events"><div class="browse-bar"><label class="search-wrap">${icon("search")}<input id="event-search" type="search" placeholder="An event, a place, a new experience…" aria-label="Search events and venues" value="${esc(state.search)}"></label><select id="venue-filter" aria-label="Filter by venue"><option value="">Everywhere</option>${state.venues.map((v) => `<option value="${v.id}" ${String(v.id) === state.venue ? "selected" : ""}>${esc(v.name)}</option>`).join("")}</select><select id="sort-filter" aria-label="Sort events"><option value="date" ${state.sort === "date" ? "selected" : ""}>Soonest first</option><option value="price" ${state.sort === "price" ? "selected" : ""}>Price: low to high</option></select></div><div class="section-head"><h2>Worth showing up for <small id="event-count">0</small></h2><span>Your next “glad I went” moment.</span></div><div class="event-grid" id="event-grid"></div></section><aside class="experience-strip"><div class="strip-title"><span>✳</span> The experience starts here.</div><div class="strip-benefits"><span>Pick your own seat</span><span>Simple booking</span><span>Tickets in one place</span></div></aside>`;
    $("#event-search").addEventListener("input", (e) => {
      state.search = e.target.value;
      renderCards();
    });
    $("#venue-filter").addEventListener("change", (e) => {
      state.venue = e.target.value;
      renderCards();
    });
    $("#sort-filter").addEventListener("change", (e) => {
      state.sort = e.target.value;
      renderCards();
    });
    renderCards();
    if (!state.events.length)
      $("#event-grid").innerHTML = empty(
        "Your next chapter is on its way.",
        "There are no published events yet. Check back soon for something worth getting out for.",
        isAdmin() ? "go-admin" : "",
        isAdmin() ? "Create an event" : "",
      );
  }
  async function route() {
    const version = ++routeVersion;
    state.route = location.hash.slice(1) || "discover";
    if (!["discover", "tickets", "admin"].includes(state.route))
      state.route = "discover";
    document
      .querySelectorAll("[data-nav]")
      .forEach((a) =>
        a.classList.toggle("active", a.dataset.nav === state.route),
      );
    $("#breadcrumb").textContent = {
      discover: "Discover",
      tickets: "My tickets",
      admin: "Manage events",
    }[state.route];
    $("#sidebar").classList.remove("open");
    $("#menu-button").setAttribute("aria-expanded", "false");
    main.innerHTML =
      '<div class="loading" role="status">Getting things ready…</div>';
    try {
      if (!state.loaded) await loadCatalog();
      if (version !== routeVersion) return;
      if (state.route === "discover") discover();
      else if (state.route === "tickets") await showTickets(version);
      else await showAdmin(version);
    } catch (e) {
      if (version === routeVersion) main.innerHTML = errorBlock(e.message);
    }
  }
  function showModal(html) {
    modalContent.innerHTML = html;
    if (!modal.open) modal.showModal();
    else modal.scrollTop = 0;
  }
  function head(title, eyebrow = "EVENTHQ") {
    return `<div class="modal-head"><div><div class="eyebrow">${esc(eyebrow)}</div><h2 id="modal-title">${esc(title)}</h2></div><button class="icon-button" data-action="close" aria-label="Close dialog">×</button></div>`;
  }
  function modalError(message) {
    let box = $(".form-error", modal);
    if (!box) {
      box = document.createElement("div");
      box.className = "form-error";
      box.setAttribute("role", "alert");
      ($(".modal-body", modal) || modalContent).append(box);
    }
    box.textContent = message;
    box.scrollIntoView({ block: "nearest" });
  }
  async function busy(button, action) {
    if (button?.disabled) return;
    if (button) {
      button.disabled = true;
      button.dataset.busy = "true";
    }
    try {
      await action();
    } catch (e) {
      modal.open ? modalError(e.message) : toast(e.message);
    } finally {
      if (button) {
        button.disabled = false;
        delete button.dataset.busy;
      }
      tick();
    }
  }
  function auth(mode = "login", after = null) {
    state.authMode = mode;
    state.afterAuth = after;
    showModal(
      `${head(mode === "login" ? "Welcome back." : "Make room for more.", "YOUR NEXT EXPERIENCE AWAITS")}<div class="modal-body"><p>${mode === "login" ? "Your tickets, your plans, your next great memory." : "A few details, and you’re one step closer to being there."}</p><div class="auth-tabs"><button data-action="auth-mode" data-mode="login" class="${mode === "login" ? "active" : ""}">Sign in</button><button data-action="auth-mode" data-mode="register" class="${mode === "register" ? "active" : ""}">Create account</button></div><form data-form="auth"><div class="form-grid"><label class="field full">Email address<input type="email" name="email" required maxlength="254" autocomplete="email" placeholder="you@example.com"></label><label class="field full">Password<input type="password" name="password" required minlength="12" maxlength="72" pattern="[ -~]+" autocomplete="${mode === "login" ? "current-password" : "new-password"}" placeholder="At least 12 characters"><small>12–72 printable characters.</small></label></div><div class="form-actions"><button type="submit" class="primary">${mode === "login" ? "Sign in" : "Create account"} <span>↗</span></button></div></form><p class="form-note">Your account keeps your bookings and tickets together. This preview uses simulated payments.</p></div>`,
    );
  }
  async function submitAuth(form) {
    const data = Object.fromEntries(new FormData(form)),
      token = await api(`/api/v1/auth/${state.authMode}`, {
        method: "POST",
        body: data,
      });
    state.auth = { token: token.accessToken, email: data.email.toLowerCase() };
    store("eventhq.auth", state.auth);
    restoreHold();
    updateAccount();
    modal.close();
    toast(
      state.authMode === "login"
        ? "Good to have you back."
        : "You’re in. Let’s find your next experience.",
    );
    const next = state.afterAuth;
    state.afterAuth = null;
    if (next) await next();
    else route();
  }
  async function openEvent(id) {
    state.currentEvent = state.events.find((e) => e.id === id);
    if (!state.currentEvent)
      state.currentEvent = await api(`/api/v1/events/${id}`);
    state.selected.clear();
    showModal(
      `${head(state.currentEvent.name, "FIND YOUR SEAT")}<div class="loading">Checking live availability…</div>`,
    );
    state.inventory = await api(`/api/v1/events/${id}/seats`);
    renderSeats();
  }
  function renderSeats() {
    const e = state.currentEvent,
      started = new Date(e.startsAt) <= new Date();
    showModal(
      `${head(e.name, "MAKE IT YOUR MOMENT")}<div class="modal-body"><p>${esc(venueName(e.venueId))} · ${date(e.startsAt, { day: "numeric", month: "long", year: "numeric", hour: "numeric", minute: "2-digit" })}</p><div class="stage">THE STAGE</div><div class="seat-grid">${state.inventory.map((s) => `<button class="seat ${s.tier === "VIP" ? "vip" : ""} ${state.selected.has(s.id) ? "selected" : ""}" data-action="seat" data-id="${s.id}" ${!s.available || started ? "disabled" : ""} aria-label="Seat ${esc(s.label)}, ${esc(s.tier)}, ${s.available ? "available" : "unavailable"}" aria-pressed="${state.selected.has(s.id)}">${esc(s.label)}</button>`).join("")}</div><div class="legend"><button class="text-button" data-action="refresh-seats">Refresh availability ↻</button><span><i></i>Standard</span><span><i class="vip-key"></i>VIP</span><span><i class="selected-key"></i>Selected</span><span><i class="unavailable-key"></i>Booked / held</span></div>${started ? '<p class="form-error">Booking is closed because this event has started.</p>' : ""}<div class="selection-summary"><div><strong>${state.selected.size ? `${state.selected.size} seat${state.selected.size === 1 ? "" : "s"} selected` : "A good seat is waiting."}</strong><small>Choose up to 10 seats. Your exact price is shown next.<br>Standard base price ${money(e.basePrice, e.currency)} · VIP and early-bird pricing may apply.</small></div><button class="primary" data-action="hold" ${!state.selected.size || started ? "disabled" : ""}>Reserve seats <span>↗</span></button></div><p class="form-note">Availability is live. Once reserved, your seats are held for a limited time while you confirm.</p></div>`,
    );
  }
  async function createHold() {
    if (!state.auth) {
      auth("login", createHold);
      return;
    }
    if (state.hold) {
      await resume();
      return;
    }
    const b = await api("/api/v1/bookings", {
      method: "POST",
      body: {
        eventId: state.currentEvent.id,
        eventSeatIds: [...state.selected],
      },
    });
    saveHold({ booking: b, key: `web-${crypto.randomUUID()}` });
    checkout();
    if (state.route === "discover") discover();
  }
  function checkout() {
    const b = state.hold.booking,
      e = state.events.find((e) => e.id === b.eventId);
    showModal(
      `${head("One step from being there.", "YOUR RESERVATION")}<div class="modal-body"><div class="hold-banner"><span>Your seats are saved for you.</span><strong data-timer>${timer(b)}</strong></div><div class="checkout-event"><div class="checkout-date">${e ? date(e.startsAt, { month: "short" }) : "SEAT"}<strong>${e ? date(e.startsAt, { day: "numeric" }) : b.items.length}</strong></div><div><h3>${esc(eventName(b.eventId))}</h3><p>${e ? esc(venueName(e.venueId)) : "Your reserved experience"}</p></div></div><div class="checkout-lines">${b.items.map((i) => `<div class="checkout-line"><span>Seat ${esc(i.label)}</span><span>${money(i.price, i.currency)}</span></div>`).join("")}<div class="checkout-line total"><span>Your total</span><span>${money(b.total, b.currency)}</span></div></div><p class="form-note">Demo checkout · No real money is charged. Confirming issues your tickets through the EventHQ payment simulator.</p><div class="form-actions"><button class="text-button danger" data-action="cancel-hold">Release seats</button><button class="primary" data-action="pay" data-pay>Confirm demo payment <span>↗</span></button></div></div>`,
    );
    tick();
  }
  async function resume() {
    if (!state.auth) {
      auth("login", resume);
      return;
    }
    if (!state.hold) {
      toast("There is no active reservation.");
      return;
    }
    const b = await api(`/api/v1/bookings/${state.hold.booking.id}`);
    if (b.status !== "HELD") {
      saveHold(null);
      modal.close();
      toast(
        b.status === "PAID"
          ? "This booking is already confirmed. Find it in My tickets."
          : "This reservation has expired or been released. Please select your seats again.",
      );
      route();
      return;
    }
    saveHold({ ...state.hold, booking: b });
    checkout();
  }
  async function pay() {
    if (!state.auth) {
      auth("login", resume);
      return;
    }
    const b = state.hold?.booking;
    if (!b) return;
    if (!remaining(b))
      throw new Error(
        "Your hold has expired. Close this dialog and select your seats again.",
      );
    const result = await paymentApi(
      b.id,
      { gateway: "SIMULATED", token: "tok_approved" },
      state.hold.key,
    );
    if (result.outcome === "EXPIRED") {
      saveHold(null);
      throw new Error(
        "Your reservation expired. Please choose your seats again.",
      );
    }
    if (result.outcome !== "APPROVED")
      throw new Error(
        "Payment was not approved. Your seats remain held until the timer expires.",
      );
    saveHold(null);
    showModal(
      `${head("You’re going.", "A GOOD PLAN, CONFIRMED")}<div class="modal-body success-body"><div class="success-mark">✓</div><h3>Now that’s something<br>to look forward to.</h3><p>Your tickets are ready. You can find your seat details<br>and confirmation code in My tickets.</p><button class="primary" data-action="view-tickets">See my tickets <span>↗</span></button></div>`,
    );
    await loadCatalog();
    route();
  }
  async function cancelHold() {
    if (!state.hold) return;
    await api(`/api/v1/bookings/${state.hold.booking.id}`, {
      method: "DELETE",
    });
    saveHold(null);
    modal.close();
    toast("Your seats have been released.");
    await loadCatalog();
    route();
  }
  async function showTickets(version) {
    main.innerHTML = `<div class="eyebrow">PLANS WORTH KEEPING</div><h1 class="page-heading">Your next good memory.</h1><p class="page-intro">Every ticket, every seat, every reason to get out there.</p>${holdBanner()}<div id="ticket-results">${state.auth ? '<div class="loading">Finding your tickets…</div>' : empty("Good plans live here.", "Sign in to see your tickets and continue any active reservation.", "signin", "Sign in")}</div>`;
    if (!state.auth) return;
    const tickets = await api("/api/v1/tickets");
    if (version !== routeVersion) return;
    $("#ticket-results").innerHTML = tickets.length
      ? `<div class="ticket-list">${tickets
          .map((t) => {
            const e = state.events.find((x) => x.id === t.eventId);
            return `<article class="ticket-card"><div class="ticket-accent"></div><div class="ticket-details"><div class="ticket-top"><span>BOOKING #${t.bookingId}</span><span class="pill">CONFIRMED</span></div><h3>${esc(eventName(t.eventId))}</h3><div class="ticket-info"><span>${e ? date(e.startsAt, { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" }) : "Confirmed event"}</span><span>${e ? esc(venueName(e.venueId)) : ""}</span><span>${money(t.price, t.currency)}</span></div></div><div class="ticket-stub"><small>YOUR SEAT</small><strong>${esc(t.seat)}</strong><span class="ticket-code">${esc(t.code)}</span><div class="barcode" aria-hidden="true"></div></div></article>`;
          })
          .join("")}</div>`
      : empty(
          "Your first “I was there” awaits.",
          "Find an event you love, pick your seat, and we’ll keep your ticket right here.",
          "go-discover",
          "Explore events",
        );
  }

  async function showAdmin(version) {
    if (!isAdmin()) {
      main.innerHTML = empty(
        "A space for the people behind it.",
        "Catalog management is available to administrators. Sign in with your administrator account.",
        "signin",
        "Sign in",
      );
      return;
    }
    const [events, sponsors] = await Promise.all([
      allEvents(true),
      api("/api/v1/admin/sponsors"),
    ]);
    if (version !== routeVersion) return;
    state.adminEvents = events;
    state.sponsors = sponsors;
    main.innerHTML = `<div class="eyebrow">BEHIND THE EXPERIENCE</div><h1 class="page-heading">Make something happen.</h1><p class="page-intro">Build your catalog, bring people together, and get your next event out into the world.</p><div class="admin-tabs" role="group" aria-label="Catalog sections">${["events", "venues", "sponsors"].map((t) => `<button data-action="admin-tab" data-tab="${t}" class="${state.adminTab === t ? "active" : ""}">${t[0].toUpperCase() + t.slice(1)}</button>`).join("")}</div><div id="admin-results"></div>`;
    adminTable();
  }
  function adminTable() {
    const tab = state.adminTab,
      events = tab === "events",
      venues = tab === "venues",
      records = events
        ? state.adminEvents
        : venues
          ? state.venues
          : state.sponsors;
    $("#admin-results").innerHTML =
      `<div class="admin-tools"><p>${events ? "Draft it first. Publish when you’re ready." : venues ? "Create a venue and add seats before assigning an event." : "The people helping make your events possible."}</p><button class="primary" data-action="create-${tab}">+ ${events ? "Create event" : venues ? "Add venue" : "Add sponsor"}</button></div>${records.length ? `<div class="table-wrap"><table><thead><tr><th>${events ? "EVENT" : venues ? "VENUE" : "SPONSOR"}</th><th>${events ? "STATUS" : venues ? "LOCATION" : "REFERENCE"}</th><th>ACTIONS</th></tr></thead><tbody>${records.map((r) => `<tr><td>${esc(r.name)}${events ? `<small>${date(r.startsAt, { day: "numeric", month: "short", year: "numeric" })} · ${esc(venueName(r.venueId))}</small>` : ""}</td><td>${events ? `<span class="pill ${r.published ? "" : "draft"}">${r.published ? "PUBLISHED" : "DRAFT"}</span>` : venues ? esc(r.address) : "#" + r.id}</td><td><div class="row-actions">${venues ? `<button data-action="manage-seats" data-id="${r.id}">Seats</button>` : ""}${!events || !r.published ? `<button data-action="edit-${tab}" data-id="${r.id}">Edit</button>` : ""}${events && !r.published ? `<button data-action="publish" data-id="${r.id}">Publish ↗</button>` : ""}${!events || !r.published ? `<button class="danger" data-action="delete-${tab}" data-id="${r.id}">Delete</button>` : ""}</div></td></tr>`).join("")}</tbody></table></div>` : empty("A fresh start.", "Add your first entry to start building the experience.")}`;
  }
  const input = (
    label,
    name,
    type = "text",
    value = "",
    extra = "",
    full = false,
  ) =>
    `<label class="field ${full ? "full" : ""}">${label}<input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
  function localDate(value) {
    const d = new Date(value);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  }
  function catalogForm(type, id = null) {
    const records =
        type === "events"
          ? state.adminEvents
          : type === "venues"
            ? state.venues
            : state.sponsors,
      r = records.find((x) => x.id === id),
      singular = type.slice(0, -1);
    let fields = "";
    if (type === "events") {
      if (!state.venues.length) {
        toast("Create a venue and its seats first.");
        state.adminTab = "venues";
        adminTable();
        return;
      }
      const future = new Date(Date.now() + 30 * 86400000);
      fields =
        input(
          "Event name",
          "name",
          "text",
          r?.name || "",
          'required maxlength="150"',
          true,
        ) +
        `<label class="field full">Venue<select name="venueId" required ${r ? "disabled" : ""}>${state.venues.map((v) => `<option value="${v.id}" ${r?.venueId === v.id ? "selected" : ""}>${esc(v.name)}</option>`).join("")}</select><small>The venue must have seats. Seating freezes when its first event is created.</small></label>` +
        input(
          "Starts at (your local time)",
          "startsAt",
          "datetime-local",
          localDate(r?.startsAt || future),
          "required",
        ) +
        input(
          "Early-bird cutoff (local time)",
          "earlyBirdUntil",
          "datetime-local",
          localDate(r?.earlyBirdUntil || new Date(Date.now() + 7 * 86400000)),
          "required",
        ) +
        input(
          "Standard price",
          "basePrice",
          "number",
          r?.basePrice || "1000.00",
          'required min="0.01" max="999999999999.99" step="0.01"',
          true,
        ) +
        `<div class="field full">Sponsors (optional)<div class="sponsor-options">${state.sponsors.length ? state.sponsors.map((s) => `<label><input type="checkbox" name="sponsorIds" value="${s.id}" ${r?.sponsors.some((x) => x.id === s.id) ? "checked" : ""}>${esc(s.name)}</label>`).join("") : "<small>No sponsors added yet.</small>"}</div></div>`;
    } else if (type === "venues") {
      fields =
        input(
          "Venue name",
          "name",
          "text",
          r?.name || "",
          'required maxlength="150"',
          true,
        ) +
        input(
          "Address",
          "address",
          "text",
          r?.address || "",
          'required maxlength="255"',
          true,
        ) +
        input(
          "Latitude",
          "latitude",
          "number",
          r?.latitude ?? "28.6139",
          'required min="-90" max="90" step="any"',
        ) +
        input(
          "Longitude",
          "longitude",
          "number",
          r?.longitude ?? "77.2090",
          'required min="-180" max="180" step="any"',
        );
    } else
      fields = input(
        "Sponsor name",
        "name",
        "text",
        r?.name || "",
        'required maxlength="150"',
        true,
      );
    showModal(
      `${head(`${r ? "Edit" : "Add"} ${singular}`, "BUILD YOUR CATALOG")}<div class="modal-body"><form data-form="catalog" data-type="${type}" data-id="${r?.id || ""}"><div class="form-grid">${fields}</div><div class="form-actions"><button type="button" class="secondary" data-action="close">Cancel</button><button type="submit" class="primary">${r ? "Save changes" : type === "events" ? "Create draft" : "Save " + singular} ?</button></div></form></div>`,
    );
  }
  async function saveCatalog(form) {
    const type = form.dataset.type,
      id = form.dataset.id,
      data = new FormData(form);
    let body = Object.fromEntries(data);
    if (type === "events") {
      const existing = state.adminEvents.find((e) => e.id === Number(id));
      body = {
        name: data.get("name"),
        venueId: existing ? existing.venueId : Number(data.get("venueId")),
        startsAt: new Date(data.get("startsAt")).toISOString(),
        earlyBirdUntil: new Date(data.get("earlyBirdUntil")).toISOString(),
        basePrice: Number(data.get("basePrice")),
        sponsorIds: data.getAll("sponsorIds").map(Number),
      };
    }
    if (type === "venues") {
      body.latitude = Number(body.latitude);
      body.longitude = Number(body.longitude);
    }
    await api(`/api/v1/admin/${type}${id ? "/" + id : ""}`, {
      method: id ? "PUT" : "POST",
      body,
    });
    modal.close();
    toast(
      type === "events" && !id
        ? "Draft created. Publish it when you’re ready."
        : "Saved successfully.",
    );
    await loadCatalog();
    route();
  }
  async function manageSeats(id) {
    const seats = await api(`/api/v1/venues/${id}/seats`),
      frozen = state.adminEvents.some((e) => e.venueId === id);
    showModal(
      `${head("A place for everyone.", "SEATING · " + venueName(id))}<div class="modal-body"><p>${frozen ? "Seating is frozen because this venue is already assigned to an event." : "Add seats individually, choosing STANDARD or VIP before creating your first event."}</p><div class="seats-manage"><div class="seat-grid">${seats.map((s) => `<div class="seat-label">${esc(s.label)}<small>${esc(s.tier)}</small></div>`).join("")}</div></div>${frozen ? "" : `<form data-form="seat" data-venue="${id}"><div class="form-grid">${input("Seat label", "label", "text", "", 'required maxlength="40" placeholder="e.g. A1"')}<label class="field">Tier<select name="tier"><option>STANDARD</option><option>VIP</option></select></label></div><div class="form-actions"><button type="submit" class="primary">Add seat +</button></div></form>`}</div>`,
    );
  }
  function confirmDelete(type, id) {
    const r = (
      type === "events"
        ? state.adminEvents
        : type === "venues"
          ? state.venues
          : state.sponsors
    ).find((x) => x.id === id);
    showModal(
      `${head("Remove this entry?", "CATALOG MANAGEMENT")}<div class="modal-body"><p>Delete “${esc(r?.name)}”? This cannot be undone. Entries referenced by other records cannot be removed.</p><div class="form-actions"><button class="secondary" data-action="close">Keep it</button><button class="primary" data-action="confirm-delete" data-type="${type}" data-id="${id}">Delete entry</button></div></div>`,
    );
  }
  function tick() {
    if (!state.hold) return;
    document
      .querySelectorAll("[data-timer]")
      .forEach((el) => (el.textContent = timer(state.hold.booking)));
    document
      .querySelectorAll("[data-pay]")
      .forEach(
        (el) =>
          (el.disabled =
            Boolean(el.dataset.busy) || !remaining(state.hold.booking)),
      );
  }
  document.addEventListener("click", (event) => {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const action = button.dataset.action,
      id = Number(button.dataset.id);
    if (action === "close") {
      state.afterAuth = null;
      modal.close();
      return;
    }
    if (action === "seat") {
      if (state.selected.has(id)) state.selected.delete(id);
      else {
        if (state.selected.size === 10) {
          toast("You can reserve up to 10 seats at a time.");
          return;
        }
        state.selected.add(id);
      }
      renderSeats();
      return;
    }
    if (action === "auth-mode") {
      auth(button.dataset.mode, state.afterAuth);
      return;
    }
    if (action === "signin") {
      auth();
      return;
    }
    if (
      action === "go-discover" ||
      action === "view-tickets" ||
      action === "go-admin"
    ) {
      modal.close();
      location.hash =
        action === "go-discover"
          ? "discover"
          : action === "go-admin"
            ? "admin"
            : "tickets";
      if (location.hash.slice(1) === state.route) route();
      return;
    }
    if (action === "admin-tab") {
      state.adminTab = button.dataset.tab;
      showAdmin(routeVersion).catch((e) => toast(e.message));
      return;
    }
    if (action.startsWith("create-")) {
      catalogForm(action.slice(7));
      return;
    }
    if (action.startsWith("edit-")) {
      catalogForm(action.slice(5), id);
      return;
    }
    if (action.startsWith("delete-")) {
      confirmDelete(action.slice(7), id);
      return;
    }
    busy(button, async () => {
      if (action === "retry") {
        state.loaded = false;
        await route();
      } else if (action === "event") await openEvent(id);
      else if (action === "refresh-seats")
        await openEvent(state.currentEvent.id);
      else if (action === "hold") await createHold();
      else if (action === "pay") await pay();
      else if (action === "resume") await resume();
      else if (action === "cancel-hold") await cancelHold();
      else if (action === "manage-seats") await manageSeats(id);
      else if (action === "publish") {
        await api(`/api/v1/admin/events/${id}/publish`, { method: "POST" });
        toast("Your event is live.");
        await loadCatalog();
        await route();
      } else if (action === "confirm-delete") {
        await api(`/api/v1/admin/${button.dataset.type}/${id}`, {
          method: "DELETE",
        });
        modal.close();
        toast("Entry removed.");
        await loadCatalog();
        await route();
      }
    });
  });
  modal.addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.target;
    $(".form-error", modal)?.remove();
    busy($('button[type="submit"]', form), async () => {
      if (form.dataset.form === "auth") await submitAuth(form);
      else if (form.dataset.form === "catalog") await saveCatalog(form);
      else if (form.dataset.form === "seat") {
        await api(`/api/v1/admin/venues/${form.dataset.venue}/seats`, {
          method: "POST",
          body: Object.fromEntries(new FormData(form)),
        });
        await manageSeats(Number(form.dataset.venue));
        toast("Seat added.");
      }
    });
  });
  modal.addEventListener("click", (e) => {
    if (e.target === modal) {
      const rect = modal.getBoundingClientRect();
      if (
        e.clientX < rect.left ||
        e.clientX > rect.right ||
        e.clientY < rect.top ||
        e.clientY > rect.bottom
      )
        modal.close();
    }
  });
  $("#account-button").addEventListener("click", () => {
    if (!state.auth) {
      auth();
      return;
    }
    showModal(
      `${head("Your space.", "SIGNED IN")}<div class="modal-body"><p>${esc(state.auth.email)}</p><p class="form-note">${isAdmin() ? "Administrator" : "Customer"} account · Access tokens expire after 15 minutes. Sign in again when prompted.</p><div class="form-actions"><button class="secondary" data-action="view-tickets">My tickets ↗</button></div></div>`,
    );
  });
  $("#logout-button").addEventListener("click", () => {
    clearAuth();
    modal.close();
    toast("You’ve signed out.");
    location.hash = "discover";
    route();
  });
  $("#close-menu").addEventListener("click", () => {
    $("#sidebar").classList.remove("open");
    $("#menu-button").setAttribute("aria-expanded", "false");
  });
  $("#menu-button").addEventListener("click", () => {
    const open = $("#sidebar").classList.toggle("open");
    $("#menu-button").setAttribute("aria-expanded", String(open));
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      $("#sidebar").classList.remove("open");
      $("#menu-button").setAttribute("aria-expanded", "false");
    }
  });
  $("#today").textContent = date(new Date(), {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
  window.addEventListener("hashchange", route);
  updateAccount();
  route();
  setInterval(tick, 1000);
  async function health() {
    try {
      const response = await fetch("/actuator/health"),
        data = await response.json(),
        up = response.ok && data.status === "UP";
      $("#service-status").textContent = up
        ? "Ready for good times"
        : "Service unavailable";
      $("#status-dot").className = "status-dot " + (up ? "up" : "down");
    } catch {
      $("#service-status").textContent = "Connection interrupted";
      $("#status-dot").className = "status-dot down";
    }
  }
  health();
  setInterval(health, 60000);
})();
