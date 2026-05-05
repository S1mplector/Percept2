package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Lightweight CSS-only icon factory using SVG path shapes rendered
 * via {@code -fx-shape} on a tiny {@link Region}. No image assets needed.
 */
public final class CssIcon {
  private CssIcon() {}

  // ── SVG path data ──

    private static final String PATH_SPARKLES = "M19 8.3q-.125 0-.262-.075Q18.6 8.15 18.55 8l-.8-1.75-1.75-.8q-.15-.05-.225-.188Q15.7 5.125 15.7 5t.075-.263Q15.85 4.6 16 4.55l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.187.075.138.075.263t-.075.262Q22.15 5.4 22 5.45l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075Zm0 14q-.125 0-.262-.075-.138-.075-.188-.225l-.8-1.75-1.75-.8q-.15-.05-.225-.188-.075-.137-.075-.262t.075-.262q.075-.138.225-.188l1.75-.8.8-1.75q.05-.15.188-.225.137-.075.262-.075t.263.075q.137.075.187.225l.8 1.75 1.75.8q.15.05.225.188.075.137.075.262t-.075.262q-.075.138-.225.188l-1.75.8-.8 1.75q-.05.15-.187.225-.138.075-.263.075ZM9 18.575q-.275 0-.525-.15T8.1 18l-1.6-3.5L3 12.9q-.275-.125-.425-.375-.15-.25-.15-.525t.15-.525q.15-.25.425-.375l3.5-1.6L8.1 6q.125-.275.375-.425.25-.15.525-.15t.525.15q.25.15.375.425l1.6 3.5 3.5 1.6q.275.125.425.375.15.25.15.525t-.15.525q-.15.25-.425.375l-3.5 1.6L9.9 18q-.125.275-.375.425-.25.15-.525.15Zm0-3.425L10 13l2.15-1L10 11 9 8.85 8 11l-2.15 1L8 13ZM9 12Z";
  private static final String PATH_THEATER = "M21 2h-8c-1.1 0-2 .9-2 2v3.5h1.5c1.1 0 2 .9 2 2v4.95c1.04.48 2.24.68 3.5.47 2.93-.49 5-3.17 5-6.14V4c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm4.85 4.38h-3.72c-.38 0-.63-.41-.44-.75.39-.66 1.27-1.13 2.3-1.13s1.91.47 2.3 1.14c.19.33-.06.74-.44.74zM19 7.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z M11 9H3c-1.1 0-2 .9-2 2v4.79c0 3.05 2.19 5.77 5.21 6.16C9.87 22.42 13 19.57 13 16v-5c0-1.1-.9-2-2-2zm-7 4.5c0-.55.45-1 1-1s1 .45 1 1-.45 1-1 1-1-.45-1-1zm5.3 3.25c-.38.67-1.27 1.14-2.3 1.14s-1.91-.47-2.3-1.14c-.19-.34.06-.75.44-.75h3.72c.38 0 .63.41.44.75zM9 14.5c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z";
  private static final String PATH_ROBOT = "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6c-1.1 0-2 .9-2 2v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zM7.5 11.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S9.83 13 9 13s-1.5-.67-1.5-1.5zM15 17H9c-.55 0-1-.45-1-1s.45-1 1-1h6c.55 0 1 .45 1 1s-.45 1-1 1zm0-4c-.83 0-1.5-.67-1.5-1.5S14.17 10 15 10s1.5.67 1.5 1.5S15.83 13 15 13z";
  private static final String PATH_LIGHTBULB = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm-3-3h6c.55 0 1-.45 1-1s-.45-1-1-1H9c-.55 0-1 .45-1 1s.45 1 1 1zm3-17C7.86 2 4.5 5.36 4.5 9.5c0 3.82 2.66 5.86 3.77 6.5h7.46c1.11-.64 3.77-2.68 3.77-6.5C19.5 5.36 16.14 2 12 2z";
  private static final String PATH_PLUS =
      "M11 19v-6H5v-2h6V5h2v6h6v2h-6v6h-2Z";
  private static final String PATH_PLUS_BOLD =
      "M11 19v-6H5v-2h6V5h2v6h6v2h-6v6h-2Z";
  private static final String PATH_MINUS =
      "M5 13v-2h14v2H5Z";
  private static final String PATH_ARROW_UP =
      "M11 19V6.825l-5.6 5.6L4 11l8-8 8 8-1.4 1.425-5.6-5.6V19h-2Z";
  private static final String PATH_ARROW_DOWN =
      "M11 5v12.175l-5.6-5.6L4 13l8 8 8-8-1.4-1.425-5.6 5.6V5h-2Z";
  private static final String PATH_ARROW_LEFT =
      "M11 19 3 11l8-8 1.425 1.4L6.825 10H21v2H6.825l5.6 5.6L11 19Z";
  private static final String PATH_ARROW_RIGHT =
      "M13 19l-1.425-1.4 5.6-5.6H3v-2h14.175l-5.6-5.6L13 3l8 8-8 8Z";
  private static final String PATH_SORT =
      "M3 18v-2h6v2H3Zm0-5v-2h12v2H3Zm0-5V6h18v2H3Z";
  private static final String PATH_FOLDER =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h6l2 2h8q.825 0 1.413.588Q22 7.175 22 8v10q0 .825-.587 1.413Q20.825 20 20 20H4Z";
  private static final String PATH_CLEAR_X =
      "M6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5l5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19Z";
  private static final String PATH_UNDO =
      "M7.05 14q1.225-2.075 3.337-3.287Q12.5 9.5 15 9.5q2.5 0 4.675 1.15T23.55 14H21q-1.2-1.25-2.812-1.875Q16.575 11.5 15 11.5q-1.6 0-3.187.625T9 14h2v2H5V9h2v2.85Z";
  private static final String PATH_REDO =
      "M16.95 14q-1.225-2.075-3.337-3.287Q11.5 9.5 9 9.5q-2.5 0-4.675 1.15T.45 14H3q1.2-1.25 2.813-1.875Q7.425 11.5 9 11.5q1.6 0 3.188.625T15 14h-2v2h6V9h-2v2.85Z";
  private static final String PATH_SPEECH =
      "M4 22q-.825 0-1.412-.587Q2 20.825 2 20V4q0-.825.588-1.412Q3.175 2 4 2h16q.825 0 1.413.588Q22 3.175 22 4v12q0 .825-.587 1.413Q20.825 18 20 18H8l-4 4Z";
  private static final String PATH_LIST =
      "M3 14v-2h2v2H3Zm0-4V8h2v2H3Zm0-4V4h2v2H3Zm4 8v-2h14v2H7Zm0-4V8h14v2H7Zm0-4V4h14v2H7Z";
  private static final String PATH_SEARCH =
      "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
  private static final String PATH_GRID =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18v-5h9v7H4Zm11 0v-7h9v5q0 .825-.587 1.413Q22.825 20 22 20h-7ZM2 11V6q0-.825.588-1.412Q3.175 4 4 4h7v7H2Zm11 0V4h7q.825 0 1.413.588Q22 5.175 22 6v5h-9Z";
  private static final String PATH_DOWNLOAD =
      "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
  private static final String PATH_SAVE =
      "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
  private static final String PATH_EXPAND =
      "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z";
  private static final String PATH_PALETTE =
      "M12 22q-1.25 0-2.125-.875T9 19q0-.45.163-.863.162-.412.512-.862.5-.55.863-1.163.362-.612.362-1.362 0-1.125-.8-1.937Q9.3 12 8.15 12H4.55Q3.5 12 2.75 11.25T2 9V7q0-2.075 1.463-3.538Q4.925 2 7 2h10q2.075 0 3.538 1.462Q22 4.925 22 7v5q0 2.075-1.462 3.537Q19.075 17 17 17h-1.5q-.625 0-1.062.438-.438.437-.438 1.062 0 .425.2.825.2.4.525.875.475.675.763 1.337.287.663.287 1.463 0 1.25-.875 2.125T12 22Z";
  private static final String PATH_WARNING =
      "M12 22q-.825 0-1.412-.587Q10 20.825 10 20q0-.825.588-1.413Q11.175 18 12 18q.825 0 1.413.587Q14 19.175 14 20q0 .825-.587 1.413Q13.175 22 12 22Zm-1-6V6h2v10h-2Z";
  private static final String PATH_CHECK =
      "m9.55 18-5.7-5.7 1.425-1.425L9.55 15.15l9.175-9.175L20.15 7.4 9.55 18Z";
  private static final String PATH_ERROR =
      "M12 22q-2.075 0-3.9-.787-1.825-.788-3.175-2.138-1.35-1.35-2.137-3.175Q2 14.075 2 12t.788-3.9q.787-1.825 2.137-3.175 1.35-1.35 3.175-2.137Q9.925 2 12 2t3.9.788q1.825.787 3.175 2.137 1.35 1.35 2.138 3.175Q22 9.925 22 12t-.787 3.9q-.788 1.825-2.138 3.175-1.35 1.35-3.175 2.138Q14.075 22 12 22Zm0-2q3.35 0 5.675-2.325Q20 15.35 20 12q0-3.35-2.325-5.675Q15.35 4 12 4 8.65 4 6.325 6.325 4 8.65 4 12q0 3.35 2.325 5.675Q8.65 20 12 20Zm-1-5h2v-2h-2v2Zm0-4h2V5h-2v6Z";
  private static final String PATH_LINK =
      "M7 15q-1.25 0-2.125-.875T4 12q0-1.25.875-2.125T7 9h4V7H7q-2.075 0-3.537 1.463Q2 9.925 2 12q0 2.075 1.463 3.538Q4.925 17 7 17h4v-2H7Zm1.5-2v-2h7v2h-7Zm4.5 4v-2h4q1.25 0 2.125-.875T20 12q0-1.25-.875-2.125T17 9h-4V7h4q2.075 0 3.538 1.463Q22 9.925 22 12q0 2.075-1.462 3.538Q19.075 17 17 17h-4Z";
  private static final String PATH_HOME =
      "M4 21V9l8-6 8 6v12h-6v-7h-4v7H4Z";
  private static final String PATH_COPY =
      "M9 18q-.825 0-1.412-.587Q7 16.825 7 16V4q0-.825.588-1.412Q8.175 2 9 2h9q.825 0 1.413.588Q20 3.175 20 4v12q0 .825-.587 1.413Q18.825 18 18 18H9Zm0-2h9V4H9v12Zm-4 6q-.825 0-1.412-.587Q3 20.825 3 20V6h2v14h11v2H5Zm4-6V4v12Z";
  private static final String PATH_PLAY =
      "M8 19V5l11 7-11 7Z";
  private static final String PATH_STOP =
      "M6 18V6h12v12H6Z";
  private static final String PATH_POP_OUT =
      "M19 19H5V5h7V3H5q-.825 0-1.412.588Q3 4.175 3 5v14q0 .825.588 1.413Q4.175 21 5 21h14q.825 0 1.413-.587Q21 19.825 21 19v-7h-2v7ZM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7Z";
  private static final String PATH_DOCK =
      "M18 20V4H6v16h12ZM6 2h12q.825 0 1.413.588Q20 3.175 20 4v16q0 .825-.587 1.413Q18.825 22 18 22H6q-.825 0-1.412-.587Q4 20.825 4 20V4q0-.825.588-1.412Q5.175 2 6 2ZM8 18h8v-2H8v2Zm-2 2V4v16Z";
  private static final String PATH_RECT_SELECT =
      "M3 21v-4h2v2h2v2H3Zm6 0v-2h2v2H9Zm4 0v-2h2v2h-2Zm4 0v-2h2v-2h2v4h-4ZM3 15v-2h2v2H3Zm16 0v-2h2v2h-2ZM3 11V9h2v2H3Zm16 0V9h2v2h-2ZM3 7V3h4v2H5v2H3Zm6-2V3h2v2H9Zm4 0V3h2v2h-2Zm4 2V3h4v4h-2V5h-2v2h-2Z";
  private static final String PATH_POLYGON =
      "M18.5 22q-1.45 0-2.475-1.025Q15 19.95 15 18.5q0-.325.063-.612.062-.288.162-.563l-5.05-3.05q-.4.325-.887.475-.488.15-.988.15-1.45 0-2.475-1.025Q4.8 12.85 4.8 11.4q0-.4.1-.763.1-.362.25-.662L2.7 6.375Q2.35 6.025 2.175 5.563 2 5.1 2 4.5 2 3.05 3.025 2.025 4.05 1 5.5 1q1.45 0 2.475 1.025Q9 3.05 9 4.5q0 .425-.1.812-.1.388-.3.738l2.425 3.525q.425-.325.925-.45.5-.125 1.05-.125 1.45 0 2.475 1.025Q16.5 11.05 16.5 12.5q0 .175-.025.325t-.05.275l4.8 4.25q.35-.2.725-.275.375-.075.75-.075 1.45 0 2.475 1.025Q26.2 19.05 26.2 20.5q0 1.45-1.025 2.475Q24.15 24 22.7 24q-1.45 0-2.475-1.025Q19.2 21.95 19.2 20.5q0-.35.075-.688.075-.337.25-.662l-4.75-4.225q-.4.25-.837.375-.438.125-.938.125-.35 0-.675-.075t-.65-.2L16.8 18.25q.1.25.15.525.05.275.05.575 0 1.45-1.025 2.475Q15.95 23 14.5 23q-1.45 0-2.475-1.025Q11 20.95 11 19.5q0-.325.075-.6.075-.275.225-.525l-2.45-3.575q-.35.175-.712.238-.363.062-.738.062ZM5.5 3q-.625 0-1.062.438Q4 3.875 4 4.5q0 .625.438 1.062Q4.875 6 5.5 6q.625 0 1.062-.438Q7 5.125 7 4.5q0-.625-.438-1.062Q6.125 3 5.5 3ZM13 12.5q0-.625-.438-1.062Q12.125 11 11.5 11q-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438.625 0 1.062-.438Q13 13.125 13 12.5ZM14.5 21q.625 0 1.062-.438.438-.437.438-1.062 0-.625-.438-1.062-.437-.438-1.062-.438-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438Zm8.2-2q.625 0 1.062-.438.438-.437.438-1.062 0-.625-.438-1.062-.437-.438-1.062-.438-.625 0-1.062.438-.438.437-.438 1.062 0 .625.438 1.062.437.438 1.062.438Z";
  private static final String PATH_FREEHAND =
      "M4 19v-2.825l10.875-10.875q.3-.3.725-.3.425 0 .725.3l1.4 1.425q.3.3.3.7 0 .4-.3.7L6.85 19H4Zm13.025-9.425 1.4-1.425-1.4-1.4-1.425 1.4 1.425 1.425Z";
  private static final String PATH_VISIBILITY =
      "M12 17.5q3.325 0 6.075-1.925T21.825 12q-1-3-3.75-4.925T12 5.15q-3.325 0-6.075 1.925T2.175 12q1 3 3.75 4.925T12 17.5Zm0-2q-1.45 0-2.475-1.025Q8.5 13.45 8.5 12q0-1.45 1.025-2.475Q10.55 8.5 12 8.5q1.45 0 2.475 1.025Q15.5 10.55 15.5 12q0 1.45-1.025 2.475Q13.45 15.5 12 15.5ZM12 12Z";
  private static final String PATH_VISIBILITY_OFF =
      "m19.8 22.6-3.8-3.8q-1 .475-2.025.688Q12.95 19.7 12 19.7q-3.325 0-6.075-1.925T2.175 12q.5-1.5 1.35-2.737.85-1.238 2.05-2.263L1.4 2.8l1.4-1.4 18.4 18.4-1.4 1.4Zm-7.8-7.8Zm-1.875 1.875q.65.175 1.312.25.663.075 1.438.075 2.225 0 3.8-1.5T18.3 11.5q0-.7-.112-1.325-.113-.625-.338-1.225l-2.2 2.2q-.1.45-.4 1.025-.3.575-1 .85-.7.275-1.375.113-.675-.163-1.125-.563L10.125 14l-.325.25q.15.175.325.35.175.175.425.25L10.125 14Zm5.275-5.275Z";
  private static final String PATH_MEMORY =
      "M9 21v-2H7q-.825 0-1.412-.587Q5 17.825 5 17v-2H3v-2h2v-2H3V9h2V7q0-.825.588-1.412Q7.175 5 8 5h2V3h2v2h2V3h2v2h2q.825 0 1.413.588Q20 6.175 20 7v2h2v2h-2v2h2v2h-2v2q0 .825-.587 1.413Q18.825 19 18 19h-2v2h-2v-2h-2v2h-2v-2H9v2Zm-2-4h10V7H7v10Zm2-2h6V9H9v6Zm2-2h2v-2h-2v2Z";
  private static final String PATH_PERSON =
      "M12 12q-1.65 0-2.825-1.175Q8 9.65 8 8q0-1.65 1.175-2.825Q10.35 4 12 4q1.65 0 2.825 1.175Q16 6.35 16 8q0 1.65-1.175 2.825Q13.65 12 12 12Zm-8 8v-2.8q0-.85.438-1.562.437-.713 1.162-1.088 1.55-.775 3.15-1.163Q10.35 13 12 13t3.25.387q1.6.388 3.15 1.163.725.375 1.162 1.088Q20 16.35 20 17.2V20H4Z";
  private static final String PATH_LANDSCAPE =
      "M3 21 10.5 11l5.5 7h4L16 11l4-5.5L25.5 21H3ZM6.4 19h15.2l-5.6-7.5-4.5 6-3.1-4-2 5.5Z";
  private static final String PATH_DOCUMENT =
      "M6 22q-.825 0-1.412-.587Q4 20.825 4 20V4q0-.825.588-1.412Q5.175 2 6 2h8l6 6v12q0 .825-.587 1.413Q18.825 22 18 22H6Zm7-13V4H6v16h12V9h-5Z";
  private static final String PATH_EDIT =
      "M5 19h1.4l8.625-8.625-1.4-1.4L5 17.6V19ZM19.3 8.925l-4.25-4.2 1.4-1.4q.575-.575 1.413-.575.837 0 1.412.575l1.4 1.4q.575.575.6 1.388.025.812-.55 1.387L19.3 8.925ZM17.85 10.4 7.25 21H3v-4.25l10.6-10.6 4.25 4.25Z";
  private static final String PATH_DELETE =
      "M7 21q-.825 0-1.412-.587Q5 19.825 5 19V6H4V4h5V3h6v1h5v2h-1v13q0 .825-.587 1.413Q17.825 21 17 21H7ZM17 6H7v13h10V6ZM9 17h2V8H9v9Zm4 0h2V8h-2v9ZM7 6v13V6Z";
  private static final String PATH_TIMELINE =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h16q.825 0 1.413.588Q22 5.175 22 6v12q0 .825-.587 1.413Q20.825 20 20 20H4Zm0-2h16V6H4v12Zm3-2h10v-2H7v2Zm-4-4h2V8H3v4Zm4 0h14V8H7v4ZM4 6v12V6Z";
  private static final String PATH_ROCKET =
      "M14.05 18.3 12 16.25v-3.4l2.05-2.05H17.4l2.05 2.05v3.4l-2.05 2.05h-3.35ZM12 22v-3H8v3h4Zm0-4v-3.4l2.05-2.05h3.35L19.45 14.6v3.4ZM8 14H5v-4h3v4Zm4-4V6.6L9.95 4.55H6.6L4.55 6.6v3.4H8v-4h4Zm12 4v-4h-3v4h3Zm0-4h-3V6.6L18.95 4.55h-3.35L13.55 6.6V10h4v-4h4v4ZM8 6H5V3h3v3Zm12 0h-3V3h3v3Z";
  private static final String PATH_MOVIE =
      "M4 20q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h16q.825 0 1.413.588Q22 5.175 22 6v12q0 .825-.587 1.413Q20.825 20 20 20H4Zm0-2h16V6h-2v2h-2V6h-2v2h-2V6h-2v2H8V6H6v2H4v10Zm6-1l5.5-4-5.5-4v8Z";
  private static final String PATH_LABEL =
      "M17.65 20H4q-.825 0-1.412-.587Q2 18.825 2 18V6q0-.825.588-1.412Q3.175 4 4 4h13.65q.425 0 .8.175.375.175.65.475l4.6 5.35q.35.4.35.925t-.35.925l-4.6 5.35q-.275.3-.65.475-.375.175-.8.175ZM17 18l4.25-5L17 8H4v10h13Z";
  private static final String PATH_AUTO =
      "M10 20l-1.6-5.4L3 13l5.4-1.6L10 6l1.6 5.4L17 13l-5.4 1.6L10 20ZM20 12l-.8-2.2L17 9l2.2-.8L20 6l.8 2.2L23 9l-2.2.8L20 12Zm0-12l-.8 2.2L17 3l2.2.8L20 6l.8-2.2L23 3l-2.2-.8L20 0Z";
  private static final String PATH_SETTINGS =
      "M19.4 13q.05-.25.075-.55.025-.3.025-.5 0-.25-.025-.5t-.075-.55l2.1-1.65q.2-.15.25-.425.05-.275-.05-.525l-2-3.45q-.15-.25-.4-.35-.25-.1-.5-.05l-2.45 1q-.5-.4-1.1-.75-.6-.35-1.3-.55l-.4-2.65q-.05-.25-.25-.45-.2-.2-.45-.2h-4q-.25 0-.45.2-.2.2-.25.45l-.4 2.65q-.7.2-1.3.55-.6.35-1.1.75l-2.45-1q-.25-.05-.5.05-.25.1-.4.35l-2 3.45q-.1.25-.05.525.05.275.25.425l2.1 1.65q-.05.25-.075.55-.025.3-.025.5 0 .25.025.5t.075.55l-2.1 1.65q-.2.15-.25.425-.05.275.05.525l2 3.45q.15.25.4.35.25.1.5.05l2.45-1q.5.4 1.1.75.6.35 1.3.55l.4 2.65q.05.25.25.45.2.2.45.2h4q.25 0 .45-.2.2-.2.25-.45l.4-2.65q.7-.2 1.3-.55.6-.35 1.1-.75l2.45 1q.25.05.5-.05.25-.1.4-.35l2-3.45q.1-.25.05-.525-.05-.275-.25-.425l-2.1-1.65ZM12 15.5q-1.45 0-2.475-1.025Q8.5 13.45 8.5 12q0-1.45 1.025-2.475Q10.55 8.5 12 8.5q1.45 0 2.475 1.025Q15.5 10.55 15.5 12q0 1.45-1.025 2.475Q13.45 15.5 12 15.5Z";

  // ── Factory methods ──

    public static Region sparkles(String color) { return icon(PATH_SPARKLES, color, 14); }
  public static Region theater(String color) { return icon(PATH_THEATER, color, 14); }
  public static Region robot(String color) { return icon(PATH_ROBOT, color, 14); }
  public static Region lightbulb(String color) { return icon(PATH_LIGHTBULB, color, 14); }
  public static Region plus(String color)     { return icon(PATH_PLUS, color, 14); }
  public static Region plusBold(String color) { return icon(PATH_PLUS_BOLD, color, 14); }
  public static Region minus(String color)    { return icon(PATH_MINUS, color, 14); }
  public static Region arrowUp(String color)  { return icon(PATH_ARROW_UP, color, 12); }
  public static Region arrowDown(String color){ return icon(PATH_ARROW_DOWN, color, 12); }
  public static Region arrowLeft(String color){ return icon(PATH_ARROW_LEFT, color, 12); }
  public static Region arrowRight(String color){ return icon(PATH_ARROW_RIGHT, color, 12); }
  public static Region sort(String color)     { return icon(PATH_SORT, color, 14); }
  public static Region folder(String color)   { return icon(PATH_FOLDER, color, 14); }
  public static Region clearX(String color)   { return icon(PATH_CLEAR_X, color, 13); }
  public static Region undo(String color)     { return icon(PATH_UNDO, color, 14); }
  public static Region redo(String color)     { return icon(PATH_REDO, color, 14); }
  public static Region speech(String color)   { return icon(PATH_SPEECH, color, 14); }
  public static Region list(String color)     { return icon(PATH_LIST, color, 14); }
  public static Region search(String color)   { return icon(PATH_SEARCH, color, 14); }
  public static Region grid(String color)     { return icon(PATH_GRID, color, 14); }
  public static Region palette(String color)  { return icon(PATH_PALETTE, color, 14); }
  public static Region warning(String color)  { return icon(PATH_WARNING, color, 14); }
  public static Region download(String color)  { return icon(PATH_DOWNLOAD, color, 14); }
  public static Region save(String color)      { return icon(PATH_SAVE, color, 14); }
  public static Region expand(String color)    { return icon(PATH_EXPAND, color, 14); }
  public static Region check(String color)     { return icon(PATH_CHECK, color, 14); }
  public static Region error(String color)     { return icon(PATH_ERROR, color, 14); }
  public static Region link(String color)      { return icon(PATH_LINK, color, 14); }
  public static Region home(String color)      { return icon(PATH_HOME, color, 14); }
  public static Region copy(String color)      { return icon(PATH_COPY, color, 14); }
  public static Region play(String color)      { return icon(PATH_PLAY, color, 14); }
  public static Region stop(String color)      { return icon(PATH_STOP, color, 12); }
  public static Region popOut(String color)    { return icon(PATH_POP_OUT, color, 14); }
  public static Region dock(String color)      { return icon(PATH_DOCK, color, 14); }
  public static Region rectSelect(String color) { return icon(PATH_RECT_SELECT, color, 14); }
  public static Region polygon(String color)    { return icon(PATH_POLYGON, color, 14); }
  public static Region freehand(String color)   { return icon(PATH_FREEHAND, color, 14); }
  public static Region visibility(String color) { return icon(PATH_VISIBILITY, color, 14); }
  public static Region visibilityOff(String color) { return icon(PATH_VISIBILITY_OFF, color, 14); }
  public static Region memory(String color) { return icon(PATH_MEMORY, color, 14); }
  public static Region person(String color) { return icon(PATH_PERSON, color, 14); }
  public static Region landscape(String color) { return icon(PATH_LANDSCAPE, color, 14); }
  public static Region document(String color) { return icon(PATH_DOCUMENT, color, 14); }
  public static Region edit(String color) { return icon(PATH_EDIT, color, 14); }
  public static Region delete(String color) { return icon(PATH_DELETE, color, 14); }
  public static Region timeline(String color) { return icon(PATH_TIMELINE, color, 14); }
  public static Region rocket(String color) { return icon(PATH_ROCKET, color, 14); }
  public static Region movie(String color) { return icon(PATH_MOVIE, color, 14); }
  public static Region label(String color) { return icon(PATH_LABEL, color, 14); }
  public static Region auto(String color) { return icon(PATH_AUTO, color, 14); }
  public static Region settings(String color) { return icon(PATH_SETTINGS, color, 14); }

  /** Convenience: icon at default muted color. */
    public static Region sparkles() { return sparkles("#b0b8c8"); }
  public static Region theater() { return theater("#b0b8c8"); }
  public static Region robot() { return robot("#b0b8c8"); }
  public static Region lightbulb() { return lightbulb("#b0b8c8"); }
  public static Region plus()     { return plus("#b0b8c8"); }
  public static Region plusBold() { return plusBold("#b0b8c8"); }
  public static Region minus()    { return minus("#b0b8c8"); }
  public static Region arrowUp()  { return arrowUp("#b0b8c8"); }
  public static Region arrowDown(){ return arrowDown("#b0b8c8"); }
  public static Region arrowLeft(){ return arrowLeft("#b0b8c8"); }
  public static Region arrowRight(){ return arrowRight("#b0b8c8"); }
  public static Region sort()     { return sort("#b0b8c8"); }
  public static Region folder()   { return folder("#b0b8c8"); }
  public static Region clearX()   { return clearX("#b0b8c8"); }
  public static Region undo()     { return undo("#b0b8c8"); }
  public static Region redo()     { return redo("#b0b8c8"); }
  public static Region speech()   { return speech("#b0b8c8"); }
  public static Region list()     { return list("#b0b8c8"); }
  public static Region search()   { return search("#b0b8c8"); }
  public static Region grid()     { return grid("#b0b8c8"); }
  public static Region palette()  { return palette("#b0b8c8"); }
  public static Region warning()  { return warning("#b0b8c8"); }
  public static Region download() { return download("#b0b8c8"); }
  public static Region save()     { return save("#b0b8c8"); }
  public static Region expand()   { return expand("#b0b8c8"); }
  public static Region check()    { return check("#b0b8c8"); }
  public static Region error()    { return error("#b0b8c8"); }
  public static Region link()     { return link("#b0b8c8"); }
  public static Region home()     { return home("#b0b8c8"); }
  public static Region copy()     { return copy("#b0b8c8"); }
  public static Region play()     { return play("#b0b8c8"); }
  public static Region stop()     { return stop("#b0b8c8"); }
  public static Region popOut()   { return popOut("#b0b8c8"); }
  public static Region dock()     { return dock("#b0b8c8"); }
  public static Region rectSelect(){ return rectSelect("#b0b8c8"); }
  public static Region polygon()  { return polygon("#b0b8c8"); }
  public static Region freehand() { return freehand("#b0b8c8"); }
  public static Region visibility() { return visibility("#b0b8c8"); }
  public static Region visibilityOff() { return visibilityOff("#b0b8c8"); }
  public static Region memory() { return memory("#b0b8c8"); }
  public static Region person() { return person("#b0b8c8"); }
  public static Region landscape() { return landscape("#b0b8c8"); }
  public static Region document() { return document("#b0b8c8"); }
  public static Region edit() { return edit("#b0b8c8"); }
  public static Region delete() { return delete("#b0b8c8"); }
  public static Region timeline() { return timeline("#b0b8c8"); }
  public static Region rocket() { return rocket("#b0b8c8"); }
  public static Region movie() { return movie("#b0b8c8"); }
  public static Region label() { return label("#b0b8c8"); }
  public static Region auto() { return auto("#b0b8c8"); }
  public static Region settings() { return settings("#b0b8c8"); }

  /**
   * Creates a section header label with a leading CSS icon.
   */
  public static HBox iconLabel(Region icon, String text, String style) {
    Label label = new Label(text);
    label.setStyle(style);
    HBox box = new HBox(6, icon, label);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  // ── Core builder ──

  public static <T extends Region> T prepare(T region) {
    if (!region.getStyleClass().contains("jvn-fx-icon")) {
      region.getStyleClass().add("jvn-fx-icon");
    }
    region.setScaleShape(true);
    region.setCenterShape(true);
    region.setCacheShape(true);
    region.setSnapToPixel(true);
    region.setPickOnBounds(false);
    region.setMouseTransparent(true);
    return region;
  }

  public static Region icon(String svgPath, String color, double size) {
    Region r = prepare(new Region());
    r.setMinSize(size, size);
    r.setMaxSize(size, size);
    r.setPrefSize(size, size);
    r.setStyle(
        "-fx-shape: '" + svgPath + "';"
        + " -fx-background-color: " + color + ";"
        + " -fx-min-width: " + size + ";"
        + " -fx-min-height: " + size + ";"
        + " -fx-max-width: " + size + ";"
        + " -fx-max-height: " + size + ";"
    );
    return r;
  }
}
