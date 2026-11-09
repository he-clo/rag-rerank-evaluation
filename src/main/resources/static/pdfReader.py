import fitz
import sys
import json
#PyMuPDF
def blocks_to_paragraphs(blocks, page_h, y_gap=6):
    """
    blocks: get_text("blocks") 已排序
    每个 b: (x0,y0,x1,y1,text,block_no,block_type)
    按垂直邻近合并成段落。
    """
    paras = []
    cur_text = ""
    cur_y1 = None
    cur_page = None

    for b in blocks:
        x0, y0, x1, y1, txt, bn, bt = b
        txt = txt.strip()
        if not txt:
            continue

        # 跳过太靠顶/底的可疑页眉页脚，可按实际调
        if y0 < 40 or y1 > page_h - 40:
            continue

        if cur_text == "":
            cur_text = txt
            cur_y1 = y1
        else:
            # 距离近则合并；否则压入上一段
            if abs(y0 - cur_y1) <= y_gap:
                # 如果上一块末尾不是标点/连字符，补空格
                if not cur_text.endswith(("-", "—")):
                    cur_text += " "
                cur_text += txt
                cur_y1 = y1
            else:
                paras.append(cur_text)
                cur_text = txt
                cur_y1 = y1

    if cur_text:
        paras.append(cur_text)
    return paras

def extract_paragraphs_merged(pdf_path):
    doc = fitz.open(pdf_path)
    out = []

    for pi, page in enumerate(doc):
        blocks = page.get_text("blocks", sort=True)
        paras = blocks_to_paragraphs(blocks, page.rect.height, y_gap=6)
        for p in paras:
            out.append({"page": pi + 1, "text": p})

    doc.close()
    return out

if __name__=="__main__":
    file_path=sys.argv[2]
    data=extract_paragraphs_merged(file_path)

    from itertools import groupby
    # key 用 page 分组
    groups = groupby(data, key=lambda x: x["page"])

    for page, items in groups:
        for idx, item in enumerate(items, start=1):
            print(page+4, idx, item["text"])