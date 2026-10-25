"""Fixtures exercise isolation, metadata/closure and truthful capture status; never launch Java."""
import copy
import tempfile
import unittest
from pathlib import Path
import zipfile
import compat_matrix as cm


class MatrixTests(unittest.TestCase):
    def setUp(self):
        parent=Path(__file__).resolve().parents[2]/'build/compat-fixtures'
        parent.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=parent)
        self.root=Path(self.temp.name)
        (self.root/'run/mods').mkdir(parents=True)
        self.mods={m:dict(id=m,version='1.0',file=m+'.jar',dependencies=[]) for m in cm.TARGETS}
        self.mods['flerovium']['dependencies']=[dict(modId='sodium',type='required',versionRange='[1.0,2.0)',side='CLIENT')]
        self.platform=dict(minecraft='1.21.1',neoforge='21.1.250',java='21')
    def tearDown(self): self.temp.cleanup()
    def test_ranges(self):
        cases=[('1.21.1','[1.21,1.21.1)',False),('1.21.1','[1.21.1]',True),
               ('21.1.250','[21.1.218,)',True),('0.8.13+mc1.21.1','[0.8.13,0.9)',True),
               ('1.21.1','[1.21.1, 1.22)',True),('1.21.1','1.21.1',True)]
        for value,spec,want in cases: self.assertEqual(cm.satisfies(value,spec),want)
        with self.assertRaises(ValueError): cm.satisfies('1.0','arbitrary')
    def test_dependency_closure(self):
        result=cm.configuration('no-sodium',self.mods,self.platform)
        self.assertEqual(set(result['expectedMods']),{'modernfix','ferritecore','lithium'})
        self.assertEqual(set(result['excludedByDependency']),{'iris','flerovium','sclp'})
    def test_iris_metadata_does_not_block_other_configs(self):
        self.mods['iris']['dependencies']=[dict(modId='minecraft',type='required',versionRange='[1.21,1.21.1)')]
        self.assertEqual(cm.configuration('all',self.mods,self.platform)['status'],'blocked_metadata')
        self.assertFalse(cm.configuration('no-iris',self.mods,self.platform)['errors'])
        self.assertEqual(cm.configuration('none',self.mods,self.platform)['expectedMods'],[])
    def test_server_only_dependency_is_not_required_on_client(self):
        self.mods['lithium']['dependencies']=[dict(modId='missing',type='required',side='SERVER')]
        self.assertFalse(cm.configuration('all',self.mods,self.platform)['errors'])
    def test_unknown_config_rejected(self):
        with self.assertRaises(ValueError): cm.configuration('../mods',self.mods,self.platform)
    def jar(self,name,mod_id):
        path=self.root/'run/mods'/name
        with zipfile.ZipFile(path,'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml',f'[[mods]]\nmodId="{mod_id}"\nversion="1.0"\n')
        return path
    def test_metadata_and_duplicate_ids(self):
        for m in cm.TARGETS: self.jar(m+'.jar',m)
        self.assertEqual(set(cm.inventory(self.root/'run/mods')),set(cm.TARGETS))
        self.jar('duplicate.jar','sodium')
        with self.assertRaisesRegex(ValueError,'Duplicate'): cm.inventory(self.root/'run/mods')
    def test_missing_mod_fails(self):
        with self.assertRaisesRegex(ValueError,'Missing'): cm.inventory(self.root/'run/mods')
    def test_source_hash_guard(self):
        p=self.jar('sodium.jar','sodium')
        hashes={p.name:cm.sha(p)}
        cm.assert_unchanged(p.parent,hashes)
        p.write_bytes(b'changed')
        with self.assertRaises(ValueError): cm.assert_unchanged(p.parent,hashes)
    def test_prepare_copies_only_selected_mods_and_saves(self):
        for m in cm.TARGETS: self.jar(m+'.jar',m)
        self.jar('unrelated.jar','ambient')
        source=self.root/'run/saves/seed';source.mkdir(parents=True)
        (source/'level.dat').write_bytes(b'original world')
        (source/'session.lock').write_bytes(b'not copied')
        (self.root/'run/flan').mkdir();(self.root/'run/flan/test.txt').write_bytes(b'content')
        artifact=self.root/'port.jar';artifact.write_bytes(b'test artifact')
        plan=dict(artifact=dict(file='port.jar',sha256=cm.sha(artifact)), inventory=self.mods,
                  sourceModHashes={p.name:cm.sha(p) for p in (self.root/'run/mods').glob('*.jar')},
                  configs=[cm.configuration('no-sodium',self.mods,self.platform)])
        out=self.root/'result';out.mkdir()
        cm.prepare(self.root,out,plan,'seed')
        game=Path(plan['configs'][0]['gameDirectory'])
        self.assertEqual({p.stem for p in (game/'mods').glob('*.jar')},{'modernfix','ferritecore','lithium'})
        self.assertFalse((game/'saves/compat-seed/session.lock').exists())
        (game/'saves/compat-seed/level.dat').write_bytes(b'modified copy')
        self.assertEqual((source/'level.dat').read_bytes(),b'original world')
        self.assertEqual((self.root/'run/flan/test.txt').read_bytes(),b'content')
        cm.assert_unchanged(self.root/'run/mods',plan['sourceModHashes'])
        with self.assertRaises(FileExistsError): cm.prepare(self.root,out,plan,'seed')
    def test_world_path_cannot_escape_source(self):
        with self.assertRaises(ValueError): cm.prepare(self.root,self.root,{},'../../elsewhere')
    def test_done_does_not_imply_capture_success(self):
        result=cm.collect(dict(expectedMods=['sodium']),'[DebugCompat] done',self.root,self.root)
        self.assertEqual(result['status'],'capture_incomplete')
        self.assertIn('loaded mod inventory mismatch',result['failures'])
    def test_complete_capture_still_needs_visual_and_shader_gui_review(self):
        (self.root/'screenshots').mkdir()
        for name in cm.EXPECTED_SHOTS: (self.root/'screenshots'/name).write_bytes(b'x'*101)
        log='[Compat] mods={sodium,sclp}\n[RenderPerf] scene=ads\n[DebugCompat] done\n'
        for t in (452,464,476): log+=f'flansmod_compat_ads_t{t}.png (ads blend=1.0)\n'
        result=cm.collect(dict(expectedMods=['sodium','sclp']),log,self.root,self.root)
        self.assertEqual(result['status'],'captured_needs_visual_review')
        self.assertFalse(result['shaderOnTested']);self.assertFalse(result['optionsGuiTested'])
        self.assertEqual(result['visualAcceptance'],'pending')
        self.assertEqual(result['performanceStatus'],'incomplete')
        crash=cm.collect(dict(expectedMods=['sodium','sclp']),log+'[Render thread/FATAL] Reported exception thrown!\n',self.root,self.root)
        self.assertEqual(crash['status'],'runtime_failed')
        mismatch=cm.collect(dict(expectedMods=['sodium','sclp'],expectedVersions={'sodium':'0.8.13'}),log,self.root,self.root)
        self.assertIn('sodium',mismatch['mismatchedVersions'])
        result=cm.collect(dict(expectedMods=['sodium']),log,self.root,self.root)
        self.assertEqual(result['unexpectedMods'],['sclp'])
    def test_none_still_needs_environment_report(self):
        result=cm.collect(dict(expectedMods=[]),'[DebugCompat] done',self.root,self.root)
        self.assertIn('no actual environment report',result['failures'])
    def test_only_measured_scene_windows_are_comparable(self):
        log='[RenderPerf] frames=100 fps=60 scene=menu phase=warmup windowNs=10000\n'
        result=cm.collect(dict(expectedMods=[]),log,self.root,self.root)
        self.assertEqual(result['missingPerformanceScenes'],['ads','gunsmith','world'])
        for scene in ('ads','gunsmith','world'):
            log+=f'[RenderPerf] frames=60 fps=60 scene={scene} phase=measure windowNs=1000000000\n'
        result=cm.collect(dict(expectedMods=[]),log,self.root,self.root)
        self.assertEqual(result['missingPerformanceScenes'],[])


if __name__=='__main__': unittest.main(verbosity=2)
