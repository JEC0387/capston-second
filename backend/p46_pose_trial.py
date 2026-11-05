"""P46 explicitly authorized bounded public research quality experiment.

Not a retry of P45: the P45 record stays spent/read-only. This separate P46
request changes the pose/garment-transfer instructions after observed failure.
No account/IP/quota change, private inputs, or paid fallback. One request only.
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "p16-device-20261104/backend"))
import p45_public_demo_trial as trial

POSE_PROMPT = (
    "Perform a clothing-only edit of IMAGE 1. IMAGE 1 is the sole authoritative "
    "source for the person, face, body, pose, hands, feet, composition and background. "
    "Keep image 1's original wide separated leg stance: the image-left knee is bent "
    "outwards and the image-right foot is planted lower. Do NOT straighten the legs, "
    "bring the feet together, re-pose the body, shift the wrists, zoom, crop, move the "
    "camera or redraw the scene. Anchor the hip, knee, ankle, toe and wrist positions "
    "to image 1. Preserve the same face, braids, expression, skin, sunglasses, black "
    "bag, hands and fingers; preserve the door, wall, pipes, graffiti, pavement and "
    "all background geometry. Only change the clothing on that existing body. "
    "IMAGE 2 supplies ONLY the pink padded jacket: transfer its large diamond "
    "quilting, pink color, broad sleeves, puffed volume, standing collar, front "
    "snap buttons and hem. Do NOT copy the second person's face, stance, body, "
    "hands, hair, beige trousers or background. Fit the jacket around image 1's "
    "unchanged arms and wrists with correct hand occlusion. IMAGE 3 supplies ONLY "
    "the cream linen pleated trousers: retain the linen texture and pleat structure, "
    "adapt the trouser legs to image 1's existing bent-leg pose without changing "
    "the model's body. Do not copy the mannequin or museum scene. IMAGE 4 supplies "
    "ONLY the silver Mary Jane shoe: generate the matching left and right pair "
    "at the exact existing foot positions and directions of image 1. Preserve "
    "the silver surface, rounded toe, bow, strap and buckle, low brown heel and "
    "outsole. Completely replace the black trainers; no old-shoe edges, duplicated "
    "shoes or extra limbs. Natural lighting, cloth folds and contact shadows. "
    "One single photograph, no collage, no text. Identity, pose, background and "
    "product detail preservation take priority over creating a new fashion pose. "
    "This is a public research visual edit, not a physical sizing guarantee."
)

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute-public-research-once", action="store_true")
    args=parser.parse_args()
    evidence = root / "experiments/P46-20261106/pose-anchored-trial-01"
    if not args.execute_public_research_once:
        sys.argv=[sys.argv[0], "--inputs", str(root / "experiments/P45-20261106/public-inputs-20261106-060727"), "--evidence", str(evidence)]
        trial.main(POSE_PROMPT)
        raise SystemExit(0)
    if evidence.exists():
        raise SystemExit("P46 evidence already exists; no repeat or new trial directory")
    evidence.mkdir()
    (evidence / "experiment-plan.json").write_text(json.dumps({
        "baseline": "P45 official-research-trial-01",
        "controlled_change": "pose-anchored prompt; same four public inputs, reviewed Space code revision, seed, dimensions; loaded weight revision unreported",
        "prompt_sha256": hashlib.sha256(POSE_PROMPT.encode()).hexdigest(),
        "driver_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "max_new_generation_attempts": 1,
        "new_user_instruction": "P46 allows approved public free inference; no production/private mode switch",
        "success_criteria": "all three garments + strict pose/background/product preservation, visual comparison",
    }, indent=2), encoding="utf-8")
    sys.argv = [sys.argv[0], "--inputs", str(root / "experiments/P45-20261106/public-inputs-20261106-060727"),
                "--evidence", str(evidence), "--execute-public-research-once"]
    trial.main(POSE_PROMPT)
