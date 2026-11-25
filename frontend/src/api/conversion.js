import { apiFetch, endpoint } from "./http.js";

const ALLOWED_EXTENSIONS = ["pdf", "docx", "pptx", "txt"];
export const MAX_FILE_BYTES = 10 * 1024 * 1024;

export function apiUrl(path) {
  return new URL(endpoint(path), window.location.origin);
}

export function fileExtension(file) {
  const name = file?.name ?? "";
  const dot = name.lastIndexOf(".");
  return dot >= 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : "";
}

export function validateFile(file) {
  if (!file) return "Choose a file before continuing.";
  if (!ALLOWED_EXTENSIONS.includes(fileExtension(file))) {
    return "Unsupported file type. Choose a PDF, Word (.docx), PowerPoint (.pptx), or text (.txt) file.";
  }
  if (file.size === 0) return "This file is empty. Please choose a file that has content.";
  if (file.size > MAX_FILE_BYTES) return "This file is too large. The limit is 10 MB.";
  return "";
}

function friendlyError(status, message) {
  if (status === 401) return "Your session expired. Please sign in again.";
  if (status === 0) return "No connection. Check your internet and try again.";
  return message || "Something went wrong. Please try again.";
}

/**
 * Sends the file to Spring Boot. The backend extracts text and runs the AI
 * provider chain or the rule-based generator. NOTHING is saved here.
 */
export async function convertDocument({ file, method, signal }) {
  const problem = validateFile(file);
  if (problem) throw new Error(problem);

  const form = new FormData();
  form.append("file", file);
  form.append("method", method);

  let res;
  try {
    res = await apiFetch("/api/lessons/convert", {
      method: "POST",
      signal,
      body: form,
    });
  } catch (err) {
    if (err?.name === "AbortError") throw err;
    throw new Error("No connection. Check that Lessonfyy's server is running and try again.");
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    let message = "";
    try {
      const data = text ? JSON.parse(text) : null;
      message = data?.message || "";
    } catch {
      message = "";
    }
    const err = new Error(friendlyError(res.status, message));
    err.status = res.status;
    throw err;
  }

  const data = await res.json().catch(() => null);
  const title = String(data?.title ?? "").trim();
  const rawSections = Array.isArray(data?.sections) ? data.sections : [];
  const sections = rawSections
    .map((section, index) => ({
      id: `generated-${Date.now()}-${index}`,
      title: String(section?.title ?? "").trim(),
      content: String(section?.content ?? "").trim(),
    }))
    .filter((section) => section.title || section.content);

  if (!title || sections.length === 0) {
    throw new Error("The generated lesson was empty. Try again or use Semantic-Based Conversion.");
  }

  const generator = res.headers.get("X-Lessonfy-Generator") || "";
  const fallback = res.headers.get("X-Lessonfy-Fallback") === "true";
  return { title, sections, generator, fallback };
}
