import re
import textwrap

def parse_ea_style(style_str, key):
    """Extracts specific parameter values (e.g., BCol, Mode) from EA semicolon strings."""
    if not style_str:
        return None
    match = re.search(r'\b' + key + r'=([^;]+)', style_str)
    return match.group(1) if match else None

def extract_ea_color(style_str, key, fallback_hex):
    """Converts EA's BGR base-10 color integers to standard RGB hex, handling -1 fallbacks."""
    raw_val = parse_ea_style(style_str, key)
    if raw_val is None or raw_val == "-1":
        return fallback_hex
    try:
        color_int = int(raw_val)
        if color_int == -1:
            return fallback_hex
        b = (color_int >> 16) & 0xFF
        g = (color_int >> 8) & 0xFF
        r = color_int & 0xFF
        return f"#{r:02x}{g:02x}{b:02x}"
    except (ValueError, TypeError):
        return fallback_hex

def clean_html_markup(text_str):
    """Strips out hidden HTML/RTF markup tags from text notes."""
    if not text_str:
        return ""
    return re.sub(r'<[^>]*>', '', text_str)

def wrap_text_to_width(text, box_width, font_size):
    """Calculates characters per line and wraps strings explicitly based on box width."""
    if not text:
        return ""
        
    # Average character width ratio at standard font sizes is roughly 0.55 to 0.6
    char_width_pixels = font_size * 0.6
    
    # Calculate how many characters can fit inside the box width (with a tiny padding buffer)
    max_chars_per_line = int((box_width - 8) / char_width_pixels)
    max_chars_per_line = max(10, max_chars_per_line) # Guarantee a minimum fallback width
    
    # Split text lines by explicit carriage returns first, then wrap wrap segments
    paragraphs = text.split('\n')
    wrapped_lines = []
    for para in paragraphs:
        if para.strip():
            wrapped_lines.extend(textwrap.wrap(para, width=max_chars_per_line))
        else:
            wrapped_lines.append("") # Preserve empty line spacing
            
    return "\n".join(wrapped_lines)
