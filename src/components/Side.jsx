export default function Side() {
  return (
    <aside className="side">
      <div className="brand">
        <svg viewBox="0 0 32 32" aria-hidden="true">
          <rect x="5" y="4" width="22" height="24" rx="4" fill="none" stroke="#9ED8C3" strokeWidth="2.5" />
          <path d="M10 12h12M10 17h8" stroke="#F4D58D" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
        Lessonfy
      </div>

      <div>
        <h1>Read at your pace. Keep what confuses you.</h1>
        <p className="lead">
          Adjust the page to the way you focus, then pin the tricky parts so you can come back to them.
        </p>
      </div>

      <div className="sample" aria-hidden="true">
        <b>Pho</b>tosynthesis <b>conv</b>erts <b>lig</b>ht <b>ene</b>rgy into <b>che</b>mical energy,{" "}
        <span className="pin a"><b>sto</b>red in <b>glu</b>cose</span>. <b>Chlo</b>rophyll <b>abs</b>orbs{" "}
        <span className="pin b"><b>mos</b>tly <b>red</b> and <b>blue</b> light</span>.
        <small>
          <span className="l1">Confusing</span>
          <span className="l2">Study this</span>
        </small>
      </div>
    </aside>
  );
}
