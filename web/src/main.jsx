import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { loadRuns } from "./RunRepository.js";
import { statistics, bestSplit, improvement } from "./domain/statistics.js";
import { achievements } from "./domain/achievements.js";
import { distance, duration, pace, dateLabel } from "./domain/formatters.js";
import RouteMap from "./components/RouteMap.jsx";
import "./styles.css";

function TrailArt() {
  return (
    <svg className="trail-art" viewBox="0 0 460 300" aria-hidden="true">
      <circle cx="365" cy="67" r="34" fill="#e9ba72" />
      <path
        d="M0 203 Q90 83 174 175 T350 139 T480 174 V300 H0"
        fill="#d5dfc4"
      />
      <path d="M0 250 Q90 162 200 226 T460 189 V300 H0" fill="#afc4a0" />
      <path
        d="M260 310 C410 225 136 246 244 189 S361 194 320 154"
        fill="none"
        stroke="#f8f2d9"
        strokeWidth="27"
      />
      <g stroke="#344e3f" strokeWidth="8" strokeLinecap="round" fill="none">
        <circle cx="214" cy="128" r="12" fill="#344e3f" stroke="none" />
        <path d="m215 146-9 34 24 25m-24-25-21 29m26-54 22 16 16-7" />
        <circle cx="270" cy="153" r="9" fill="#344e3f" stroke="none" />
        <path d="m269 167-5 25 19 18m-19-18-13 20m16-39-18-9m19 9 15 9" />
      </g>
      <g fill="#637e56">
        <path d="m56 211 16-50 16 50z" />
        <path d="m392 197 17-54 17 54z" />
      </g>
    </svg>
  );
}
function RunCard({ run, index }) {
  return (
    <a className="run-card" href={`#/runs/${run.id}`}>
      <div className="run-number">
        {String(index).padStart(2, "0")}
        <span>RUN</span>
      </div>
      <div className="run-card-body">
        <div className="eyebrow">{dateLabel(run.date)}</div>
        <h3>{run.title}</h3>
        <p>{run.comment || "함께 달린 소중한 하루"}</p>
        <div className="run-metrics">
          <strong>
            {distance(run.distanceMeters)} <small>km</small>
          </strong>
          <span>
            {duration(run.durationSeconds)} <small>시간</small>
          </span>
          <span>
            {duration(pace(run))} <small>/km</small>
          </span>
        </div>
      </div>
      <span className="arrow">↗</span>
    </a>
  );
}
function Dashboard({ runs }) {
  const stats = statistics(runs),
    badges = achievements(runs),
    better = improvement(runs);
  const next = [10000, 25000, 50000, 100000].find(
    (n) => n > stats.totalDistance,
  );
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow">LITTLE STEPS, BIG ADVENTURES</div>
          <h1>
            함께 달려서,
            <br />더 멀리 자라는 우리.
          </h1>
          <p>
            빠르지 않아도 괜찮아.
            <br />
            아빠와 나, 우리만의 달리기 모험 일지.
          </p>
          <a className="button" href="#/runs">
            우리의 발자국 보기 <span>↗</span>
          </a>
        </div>
        <TrailArt />
        <span className="hero-note">ONE RUN AT A TIME</span>
      </section>
      <section className="stats" aria-label="누적 기록">
        {[
          ["함께 달린 거리", distance(stats.totalDistance), "km", "◎"],
          ["함께한 달리기", stats.count, "번", "⚑"],
          ["가장 멀리 달린 날", distance(stats.longest), "km", "↗"],
          ["가장 빠른 1 km", duration(stats.best1k), "/km", "ϟ"],
        ].map(([label, value, unit, icon]) => (
          <div className="stat" key={label}>
            <div className="stat-label">
              {label}
              <span>{icon}</span>
            </div>
            <strong>
              {value}
              <small>{unit}</small>
            </strong>
          </div>
        ))}
      </section>
      <div className="dashboard-columns">
        <section>
          <div className="section-heading">
            <div>
              <div className="eyebrow">OUR FOOTPRINTS</div>
              <h2>최근의 발자국</h2>
            </div>
            <a href="#/runs">모두 보기 ↗</a>
          </div>
          {runs.length ? (
            runs
              .slice(0, 3)
              .map((run, i) => (
                <RunCard key={run.id} run={run} index={runs.length - i} />
              ))
          ) : (
            <div className="empty">
              첫 모험을 기다리고 있어요. 기록을 추가하면 여기에 나타나요.
            </div>
          )}
        </section>
        <aside>
          <section className="milestone">
            <span className="tag">
              {better ? "NEW PERSONAL BEST" : "NEXT ADVENTURE"}
            </span>
            <div className="milestone-icon">{better ? "✧" : "⚑"}</div>
            <h2>
              {better
                ? "조금 더 빨라진 우리!"
                : next
                  ? `다음 목적지는 ${next / 1000} km`
                  : "100 km 너머로!"}
            </h2>
            <p>
              {better
                ? `최근 달리기에서 최고 1 km를 ${duration(better.seconds)} 단축했어요.`
                : "작은 발걸음이 모여 멋진 모험이 돼요."}
            </p>
            {better && (
              <strong className="record">
                {duration(better.best)} <small>/km</small>
              </strong>
            )}
            {next && (
              <>
                <div className="progress-label">
                  <span>누적 {distance(stats.totalDistance)} km</span>
                  <span>{next / 1000} km</span>
                </div>
                <progress value={stats.totalDistance} max={next} />
                <small>
                  앞으로 {distance(next - stats.totalDistance)} km, 함께 가볼까?
                </small>
              </>
            )}
          </section>
          <section className="badge-preview">
            <div className="section-heading">
              <h2>우리의 작은 훈장</h2>
              <a href="#/achievements">모두 보기 ↗</a>
            </div>
            <div className="mini-badges">
              {badges
                .filter((b) => b.unlocked)
                .slice(0, 3)
                .map((b) => (
                  <div key={b.id}>
                    <span>{b.icon}</span>
                    <small>{b.title}</small>
                  </div>
                ))}
            </div>
            {!stats.count && <p>첫 달리기로 첫 훈장을 만나보세요.</p>}
          </section>
        </aside>
      </div>
    </>
  );
}
function Detail({ run }) {
  if (!run)
    return (
      <div className="empty">
        <h1>기록을 찾을 수 없어요.</h1>
        <a href="#/runs">목록으로 돌아가기</a>
      </div>
    );
  const best = bestSplit(run);
  return (
    <>
      <a className="back" href="#/runs">
        ← 모든 발자국
      </a>
      <header className="page-heading">
        <div className="eyebrow">
          {dateLabel(run.date)} · {run.startTime.slice(11, 16)} (기록 현지 시각)
        </div>
        <h1>{run.title}</h1>
        <p>{run.comment}</p>
      </header>
      <section className="stats detail-stats">
        {[
          [distance(run.distanceMeters), "km", "달린 거리"],
          [duration(run.durationSeconds), "", "함께한 시간"],
          [duration(pace(run)), "/km", "평균 페이스"],
        ].map(([v, u, l]) => (
          <div className="stat" key={l}>
            <div className="stat-label">{l}</div>
            <strong>
              {v}
              <small>{u}</small>
            </strong>
          </div>
        ))}
      </section>
      <div className="detail-columns">
        <section className="panel">
          <div className="section-heading">
            <h2>우리가 달린 길</h2>
            <span className="muted">출발 ● · 도착 ●</span>
          </div>
          <RouteMap route={run.route} />
        </section>
        <section className="panel">
          <h2>한 걸음씩, 1 km씩</h2>
          <p className="muted">각 구간을 달리는 데 걸린 시간</p>
          {run.splits?.length ? (
            <div className="splits">
              {run.splits.map((s, i) => (
                <div className="split" key={i}>
                  <span>
                    {s.distanceMeters === 1000
                      ? `${i + 1} km`
                      : `마지막 ${distance(s.distanceMeters)} km`}
                  </span>
                  <div className="split-track">
                    <div
                      style={{
                        width: `${Math.min(100, best ? (best / ((s.durationSeconds / s.distanceMeters) * 1000)) * 100 : 100)}%`,
                      }}
                    />
                  </div>
                  <strong>
                    {duration(s.durationSeconds)}{" "}
                    {s.distanceMeters === 1000 && s.durationSeconds === best
                      ? "★"
                      : ""}
                  </strong>
                </div>
              ))}
            </div>
          ) : (
            <p className="empty">구간 기록이 없는 달리기예요.</p>
          )}
          <p className="muted">★ 가장 빠른 온전한 1 km 구간</p>
          {run.heartRate && (
            <p>
              심박수 · 평균 {run.heartRate.average} / 최대 {run.heartRate.max}{" "}
              bpm
            </p>
          )}
          {run.cadence && <p>평균 케이던스 · {run.cadence.average} spm</p>}
        </section>
      </div>
    </>
  );
}
function App() {
  const [runs, setRuns] = useState([]),
    [status, setStatus] = useState("loading"),
    [hash, setHash] = useState(location.hash),
    [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const handler = () => {
      setHash(location.hash);
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setStatus("loading");
    loadRuns(controller.signal)
      .then((r) => {
        setRuns(r);
        setStatus("ready");
      })
      .catch((e) => {
        if (e.name !== "AbortError") setStatus("error");
      });
    return () => controller.abort();
  }, [attempt]);
  const page = hash.replace(/^#\/?/, "") || "home";
  return (
    <>
      <header className="site-header">
        <a href="#/" className="brand">
          <span className="brand-symbol">⌁</span>
          <span>
            우리의 달리기<small>FATHER & SON RUNNING CLUB</small>
          </span>
        </a>
        <nav aria-label="주 메뉴">
          {[
            ["home", "대시보드", "#/"],
            ["runs", "달리기 기록", "#/runs"],
            ["achievements", "우리의 훈장", "#/achievements"],
          ].map(([id, label, url]) => (
            <a
              key={id}
              href={url}
              aria-current={
                page === id || page.startsWith(`${id}/`) ? "page" : undefined
              }
            >
              {label}
            </a>
          ))}
        </nav>
        <span className="club-mark">
          아빠 + 나 <span>↗</span>
        </span>
      </header>
      <main>
        <div className="sample-notice">
          SAMPLE JOURNAL{" "}
          <span>
            지금은 가상의 달리기 5개로 채운 데모예요. 경로도 가상의 좌표입니다.
          </span>
        </div>
        {status === "loading" ? (
          <div className="empty" role="status">
            우리의 발자국을 불러오는 중…
          </div>
        ) : status === "error" ? (
          <div className="empty" role="alert">
            <p>기록을 불러오지 못했어요.</p>
            <button className="button" onClick={() => setAttempt((a) => a + 1)}>
              다시 시도
            </button>
          </div>
        ) : page === "home" ? (
          <Dashboard runs={runs} />
        ) : page === "runs" ? (
          <>
            <header className="page-heading">
              <div className="eyebrow">EVERY RUN IS A MEMORY</div>
              <h1>우리의 발자국</h1>
              <p>함께 달린 {runs.length}번의 소중한 순간.</p>
            </header>
            <div className="runs-grid">
              {runs.map((r, i) => (
                <RunCard key={r.id} run={r} index={runs.length - i} />
              ))}
            </div>
            {!runs.length && (
              <div className="empty">
                아직 기록이 없어요. 첫 달리기를 기다리고 있어요.
              </div>
            )}
          </>
        ) : page.startsWith("runs/") ? (
          <Detail run={runs.find((r) => r.id === page.slice(5))} />
        ) : page === "achievements" ? (
          <>
            <header className="page-heading">
              <div className="eyebrow">SMALL WINS, BIG SMILES</div>
              <h1>우리의 작은 훈장</h1>
              <p>
                경쟁보다 함께 자라는 기쁨.{" "}
                {achievements(runs).filter((b) => b.unlocked).length}개의 추억을
                모았어요.
              </p>
            </header>
            <div className="badges-grid">
              {achievements(runs).map((b) => (
                <article
                  className={`badge ${b.unlocked ? "" : "locked"}`}
                  key={b.id}
                >
                  <span className="badge-icon">{b.icon}</span>
                  <div className="eyebrow">
                    {b.unlocked ? "달성했어요" : "다음 도전"}
                  </div>
                  <h2>{b.title}</h2>
                  <p>{b.description}</p>
                </article>
              ))}
            </div>
          </>
        ) : (
          <div className="empty">
            <h1>길을 조금 벗어났네요.</h1>
            <a href="#/">대시보드로 돌아가기</a>
          </div>
        )}
      </main>
      <footer>
        <span>작은 발걸음, 오래 남을 추억.</span>
        <span>MADE FOR OUR NEXT ADVENTURE ↗</span>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
