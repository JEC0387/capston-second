import pytest

from p44_chunked_gpu_probe import compare, position_chunk, validate_dimensions


@pytest.mark.parametrize("tokens,chunk",[(6912,6912),(6912,129),(6912,127),(6912,0),(8192,128),(16,True)])
def test_full_score_or_unreviewed_shapes_are_rejected(tokens,chunk):
    with pytest.raises(ValueError,match="reviewed_sequence"):
        validate_dimensions(tokens,chunk)


def test_query128_retains_all_6912_keys_with_bounded_score():
    assert validate_dimensions(6912,128)==35_389_440
    assert validate_dimensions(16,16)==10_240


def test_position_chunks_match_contiguous_synthetic_coordinates():
    np=pytest.importorskip("numpy")
    whole=position_chunk(0,256)
    separate=np.concatenate([position_chunk(0,128),position_chunk(128,128)],axis=2)
    np.testing.assert_array_equal(whole,separate)
    assert whole.dtype==np.float32 and whole.shape==(1,1,256,64,2,2)
    np.testing.assert_array_equal(whole[0,0,0],np.broadcast_to(np.eye(2,dtype=np.float32),(64,2,2)))


def test_mismatch_or_nan_does_not_pass_numeric_gate():
    np=pytest.importorskip("numpy")
    expected=np.array([0.,1.],dtype=np.float32)
    with pytest.raises(AssertionError): compare(expected,np.array([.01,1.],dtype=np.float32))
    with pytest.raises(ValueError,match="finite"): compare(expected,np.array([0.,np.nan],dtype=np.float32))


def test_split_graph_loads_with_default_ort_optimizer(tmp_path):
    """Regression: independent unnamed Gather groups collided in ORT 1.21."""
    np=pytest.importorskip("numpy")
    pytest.importorskip("onnx")
    ort=pytest.importorskip("onnxruntime")
    from p44_chunked_gpu_probe import WEIGHT_SCHEMA, build_graphs
    weights={name:np.zeros(shape,dtype=np.float32) for name,shape in WEIGHT_SCHEMA.items()}
    for name in ("attention.norm.query_norm.scale","attention.norm.key_norm.scale"):
        weights[name].fill(1.)
    qpath,apath=build_graphs(weights,16,16,tmp_path)
    options=ort.SessionOptions()
    options.intra_op_num_threads=options.inter_op_num_threads=1
    options.enable_cpu_mem_arena=False
    qsession=ort.InferenceSession(str(qpath),sess_options=options,providers=["CPUExecutionProvider"])
    asession=ort.InferenceSession(str(apath),sess_options=options,providers=["CPUExecutionProvider"])
    q,k,v=qsession.run(None,{"x":np.ones((1,16,1280),dtype=np.float32),"pe":position_chunk(0,16)})
    actual=asession.run(None,{"q":q,"k":k,"v":v})[0]
    np.testing.assert_array_equal(actual,np.zeros((1,16,1280),dtype=np.float32))
