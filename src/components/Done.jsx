export default function Done({ result, reset }) {
  const registered = result.type === "registered";
  return (
    <div className="card" role="status">
      <h2 id="form-title" tabIndex={-1}>
        {registered ? "Account created" : "You’re signed in"}
      </h2>
      <p className="sub">
        Signed in as <b>{result.email}</b>. This is where you will land on the dashboard.
      </p>
      <button className="btn" onClick={reset}>Back to sign in</button>
    </div>
  );
}
