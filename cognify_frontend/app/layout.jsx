import "./globals.css";
export const metadata = {
  title: "Cognify — ակադեմիական աշխատանքների հարթակ",
  description: "Ձեր գաղափարից մինչև կառուցվածքային ակադեմիական փաստաթուղթ։",
};
export default function Layout({ children }) {
  return (
    <html lang="hy">
      <body>{children}</body>
    </html>
  );
}
